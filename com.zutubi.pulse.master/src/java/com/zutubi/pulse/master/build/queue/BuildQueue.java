/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.build.queue;

import com.google.common.collect.Lists;
import com.zutubi.events.EventManager;
import com.zutubi.events.PublishFlag;
import com.zutubi.i18n.Messages;
import com.zutubi.pulse.master.build.control.BuildController;
import com.zutubi.pulse.master.build.control.BuildControllerFactory;
import com.zutubi.pulse.master.events.build.BuildActivatedEvent;
import com.zutubi.pulse.master.events.build.BuildRequestEvent;

import java.util.*;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Lists.reverse;

/**
 * The build queue tracks queued and active build requests.
 * <p/>
 * A request remains queued until all of its predicates are satisfied, at which point
 * it is activated.
 * <p/>
 * Request assimilation is the process by which two requests are considered to produce
 * the exact same result and are therefore merged into one request to avoid an unnecessary
 * build.  The assimilation of requests happens at the earliest possible opportunity to
 * avoid redundant builds showing up in the queue snapshot.
 * <p/>
 * Two requests can be assimilated under the following conditions:
 * <ul>
 * <li>The requests are adjacent requests of the same project and source in the queue (i.e. owner
 * and source both match, and the target is last with this property in the current queue)</li>
 * <li>The trigger options replaceable field must be true for both requests</li>
 * <li>The existing/target request build must not have commenced</li>
 * <li>For extended builds with multiple related requests, all of the requests must be
 * assimilated or none of them will be assimilated.</li>
 * </ul>
 */
public class BuildQueue
{
    private static final Messages I18N = Messages.getInstance(BuildQueue.class);

    private BuildControllerFactory buildControllerFactory;
    private BuildRequestRegistry buildRequestRegistry;
    private EventManager eventManager;

    /**
     * The list of currently queued requests.
     */
    private LinkedList<QueuedRequest> queuedRequests = new LinkedList<QueuedRequest>();

    /**
     * The list of activated requests.
     */
    private LinkedList<ActivatedRequest> activatedRequests = new LinkedList<ActivatedRequest>();

    /**
     * When paused, this build queue will suspend activating requests until
     * the queue is resumed.
     */
    private volatile boolean paused = false;

    /**
     * When stopped, this build queue will stop activating requests.
     */
    private volatile boolean stopped = false;

    /**
     * Enqueue the requests.
     * <p/>
     * Those requests that are placed earlier in the list will be given the opportunity
     * to be activated ahead of those that appear later in the list.
     *
     * @param requests the requests to be queued.
     */
    public synchronized void enqueue(Iterable<QueuedRequest> requests)
    {
        if (assimilateRequests(requests))
        {
            return;
        }

        for (QueuedRequest request : requests)
        {
            queuedRequests.add(0, request);
            buildRequestRegistry.requestQueued(request.getRequest());
        }

        activateWhatWeCan();
    }

    /**
     * Notifies the queue that a build is commencing.  After this point the request cannot participate in assimilation.
     *
     * @param buildId id of the build that is commencing
     */
    public synchronized void commencing(long buildId)
    {
        for (ActivatedRequest request : activatedRequests)
        {
            if (request.getController().getBuildResultId() == buildId)
            {
                request.buildCommenced();
            }
        }
    }
    /**
     * Enqueue the requests.
     *
     * @param requests the requests to enqueue.
     * @see #enqueue(Iterable)
     */
    public synchronized void enqueue(QueuedRequest... requests)
    {
        enqueue(Arrays.asList(requests));
    }

    /**
     * Cancel the queued request with the specified id.  If no such queued request exists,
     * or if the request has been activated, no change is made and false is returned.
     *
     * @param requestId the id of the request to be cancelled.
     * @return true if a queued request matching the request id was located and cancelled,
     *         false otherwise.
     */
    public synchronized boolean cancel(long requestId)
    {
        QueuedRequest requestToCancel = find(queuedRequests, new HasIdPredicate<QueuedRequest>(requestId), null);

        if (requestToCancel != null)
        {
            queuedRequests.remove(requestToCancel);
            buildRequestRegistry.requestCancelled(requestToCancel.getRequest());

            activateWhatWeCan();
        }

        return requestToCancel != null;
    }

    /**
     * Complete the activated request with the specified id.  If no such activated request exists,
     * or if the request has is still queued, no change is made and false is returned.
     *
     * @param requestId the id of the request to be completed
     * @return true if an activated request matching the request is was located and completed,
     *         false otherwise.
     */
    public synchronized boolean complete(long requestId)
    {
        ActivatedRequest completedRequest = find(activatedRequests, new HasIdPredicate<ActivatedRequest>(requestId), null);

        if (completedRequest != null)
        {
            activatedRequests.remove(completedRequest);

            activateWhatWeCan();
        }

        return completedRequest != null;
    }

    /**
     * Stop the queue from activating any further requests.  This is
     * permanent.
     * <p/>
     * To temporarily stop the activation of requests, see {@link #pauseActivation()}
     */
    public synchronized void stop()
    {
        stopped = true;
    }

    /**
     * Pause the activation of the build queue.  This will allow multiple related
     * actions to be taken without for accidental activation of a request.
     * <p/>
     * Important note.  To ensure that access to this queue is thread safe during
     * the extended processing, make sure that you synchronise on this instance.
     * <p/>
     * For example:
     * <code>
     * synchronized(buildQueue)
     * {
     * try
     * {
     * buildQueue.pauseActivation();
     * buildQueue.cancel(request1);
     * buildQueue.cancel(request2);
     * ....
     * }
     * finally
     * {
     * buildQueue.resumeActivation();
     * }
     * }
     * </code>
     *
     * @throws IllegalStateException    if the current thread does not hold a lock on the build queue.
     * @throws IllegalArgumentException if the build queue is already paused.
     */
    public void pauseActivation()
    {
        if (!Thread.holdsLock(this))
        {
            throw new IllegalStateException(I18N.format("queue.lock.required"));
        }

        if (paused)
        {
            throw new IllegalStateException(I18N.format("queue.already.paused"));
        }

        this.paused = true;
    }

    /**
     * Un-pause the activation of this build queue.
     *
     * @throws IllegalStateException    if the current thread does not hold a lock on the build queue.
     * @throws IllegalArgumentException if the build queue is not already paused.
     * @see #pauseActivation()
     */
    public void resumeActivation()
    {
        if (!Thread.holdsLock(this))
        {
            throw new IllegalStateException(I18N.format("queue.lock.required"));
        }

        if (!paused)
        {
            throw new IllegalStateException(I18N.format("queue.not.paused"));
        }

        this.paused = false;

        activateWhatWeCan();
    }

    private synchronized void activateWhatWeCan()
    {
        // activation has been disabled either temporarily or permanently
        if (paused || stopped)
        {
            return;
        }

        List<ActivatedRequest> toActivateRequests = new LinkedList<ActivatedRequest>();
        List<QueuedRequest> queueSnapshot = new LinkedList<QueuedRequest>();
        queueSnapshot.addAll(queuedRequests);

        // We need to update the queuedRequest and activatedRequest lists as we go to
        // ensure that subsequent .satisfied() checks have an accurate state to work with.
        for (QueuedRequest queuedRequest : reverse(queueSnapshot))
        {
            if (queuedRequest.satisfied())
            {
                queuedRequests.remove(queuedRequest);

                ActivatedRequest activatedRequest = new ActivatedRequest(queuedRequest.getRequest());
                activatedRequests.add(0, activatedRequest);
                toActivateRequests.add(activatedRequest);
            }
        }

        // Now we go through and finish activating the newly activated requests.
        for (ActivatedRequest activatedRequest : toActivateRequests)
        {
            BuildController controller = buildControllerFactory.create(activatedRequest.getRequest());
            long buildNumber = controller.start();
            if (buildNumber != 0)
            {
                activatedRequest.setController(controller);

                buildRequestRegistry.requestActivated(activatedRequest.getRequest(), buildNumber);
                eventManager.publish(new BuildActivatedEvent(this, activatedRequest.getRequest()), PublishFlag.DEFERRED);
            }
            else
            {
                activatedRequests.remove(activatedRequest);
                buildRequestRegistry.requestRejected(activatedRequest.getRequest(), I18N.format("rejected.with.failure"));
            }
        }
    }

    /**
     * Assimilate the requests into the existing queued entries if possible.
     *
     * @param requests  the requests to be assimilated.
     * @return true if the requests were assimilated, false otherwise.
     */
    private boolean assimilateRequests(Iterable<QueuedRequest> requests)
    {
        Map<QueuedRequest, RequestHolder> assimilationTargets = new HashMap<QueuedRequest, RequestHolder>();
        for (QueuedRequest source : requests)
        {
            if (!isReplaceable(source))
            {
                return false;
            }

            RequestHolder target = getLatestExistingRequestWithOwnerAndSource(source);
            if (isReplaceable(target))
            {
                assimilationTargets.put(source, target);
            }
            else
            {
                return false;
            }
        }

        // If any of the builds have commenced, then we can not assimilate it, so bail on the assimilation of the rest.
        if (any(assimilationTargets.values(), new BuildHasCommencedPredicate()))
        {
            return false;
        }

        // handle the assimilation of all of the requests.
        for (QueuedRequest source : assimilationTargets.keySet())
        {
            RequestHolder target = assimilationTargets.get(source);

            BuildRequestEvent targetRequest = target.getRequest();
            BuildRequestEvent sourceRequest = source.getRequest();
            targetRequest.setRevision(sourceRequest.getRevision());
            buildRequestRegistry.requestAssimilated(sourceRequest, targetRequest.getId());
        }

        return true;
    }

    private RequestHolder getLatestExistingRequestWithOwnerAndSource(RequestHolder source)
    {
        // the back of the queue is the start of the list.
        QueuedRequest candidate = find(queuedRequests, new HasOwnerAndSourcePredicate<QueuedRequest>(source), null);
        if (candidate != null)
        {
            return candidate;
        }
        return find(activatedRequests, new HasOwnerAndSourcePredicate<ActivatedRequest>(source), null);
    }

    private boolean isReplaceable(RequestHolder target)
    {
        return target != null && target.getRequest().getOptions().isReplaceable();
    }

    /**
     * Get the list of queued requests.
     *
     * @return the list of queued requests.
     */
    public synchronized List<QueuedRequest> getQueuedRequests()
    {
        return new LinkedList<QueuedRequest>(queuedRequests);
    }

    /**
     * Retrieve all of the queued requests that belong to the specified owner.
     *
     * @param owner the owner of the queued requests.
     * @return a list of queued requests belonging to the specified owner.
     */
    public synchronized Collection<QueuedRequest> getQueuedRequestsByOwner(Object owner)
    {
        return Lists.newArrayList(filter(queuedRequests, new HasOwnerPredicate<QueuedRequest>(owner)));
    }

    /**
     * Get the list of activated requests.
     *
     * @return the list of activated requests.
     */
    public synchronized List<ActivatedRequest> getActivatedRequests()
    {
        return new LinkedList<ActivatedRequest>(activatedRequests);
    }

    /**
     * Retrieve all of teh activated requests that belong to the specified owner.
     *
     * @param owner the owner of the activated requests.
     * @return a list of activated requests belonging to the specified owner.
     */
    public synchronized Collection<ActivatedRequest> getActivatedRequestsByOwner(Object owner)
    {
        return Lists.newArrayList(filter(activatedRequests, new HasOwnerPredicate<ActivatedRequest>(owner)));
    }

    /**
     * Return a count of the number of activated requests.
     *
     * @return the number of activated requests.
     */
    public synchronized int getActivatedRequestCount()
    {
        return getActivatedRequests().size();
    }

    /**
     * Get the list of requests, both activated and queued, that are
     * associated with the specified meta build id.
     *
     * @param metaBuildId the meta build id identifying the requests to be retrieved.
     * @return a list of requests associated with the specified meta build id.
     */
    public synchronized List<RequestHolder> getMetaBuildRequests(long metaBuildId)
    {
        LinkedList<RequestHolder> requests = new LinkedList<RequestHolder>();
        requests.addAll(filter(queuedRequests, new HasMetaIdPredicate<QueuedRequest>(metaBuildId)));
        requests.addAll(filter(activatedRequests, new HasMetaIdPredicate<ActivatedRequest>(metaBuildId)));
        return requests;
    }

    /**
     * Get the identified request.
     *
     * @param requestId the identifier of the request to be retrieved.
     * @return the build request event matching the id.
     */
    public synchronized BuildRequestEvent getRequest(long requestId)
    {
        RequestHolder request = find(queuedRequests, new HasIdPredicate<QueuedRequest>(requestId), null);
        if (request == null)
        {
            request = find(activatedRequests, new HasIdPredicate<ActivatedRequest>(requestId), null);
        }
        if (request != null)
        {
            return request.getRequest();
        }
        return null;
    }

    /**
     * Returns true if the specified owner has an active or queued request.
     *
     * @param owner the owner of the request
     * @return true if the specified owner has a request in the queue, false otherwise
     */
    public synchronized boolean hasRequest(Object owner)
    {
        return getRequestsByOwner(owner).size() > 0;
    }

    /**
     * Returns a list of build requests for the specified owner.
     *
     * @param owner the owner of the request
     * @return a list of build request event instances associated with the specified owner.
     */
    public synchronized List<BuildRequestEvent> getRequestsByOwner(Object owner)
    {
        List<BuildRequestEvent> byOwner = new LinkedList<BuildRequestEvent>();
        byOwner.addAll(transform(filter(queuedRequests, new HasOwnerPredicate<QueuedRequest>(owner)), new ExtractRequestFunction<QueuedRequest>()));
        byOwner.addAll(transform(filter(activatedRequests, new HasOwnerPredicate<ActivatedRequest>(owner)), new ExtractRequestFunction<ActivatedRequest>()));
        return byOwner;
    }

    /**
     * Get a snapshot of the internal state of the build queue as it is right now.
     *
     * @return the queue snapshot.
     */
    public synchronized BuildQueueSnapshot getSnapshot()
    {
        BuildQueueSnapshot snapshot = new BuildQueueSnapshot();
        snapshot.addAllActivatedRequests(activatedRequests);
        snapshot.addAllQueuedRequests(queuedRequests);
        return snapshot;
    }

    public void setBuildRequestRegistry(BuildRequestRegistry buildRequestRegistry)
    {
        this.buildRequestRegistry = buildRequestRegistry;
    }

    public void setBuildControllerFactory(BuildControllerFactory buildControllerFactory)
    {
        this.buildControllerFactory = buildControllerFactory;
    }

    public void setEventManager(EventManager eventManager)
    {
        this.eventManager = eventManager;
    }
}
