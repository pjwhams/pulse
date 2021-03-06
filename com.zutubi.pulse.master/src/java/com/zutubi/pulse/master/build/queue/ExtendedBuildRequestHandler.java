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

import com.zutubi.pulse.master.build.queue.graph.BuildGraphData;
import com.zutubi.pulse.master.build.queue.graph.GraphBuilder;
import com.zutubi.pulse.master.build.queue.graph.GraphFilters;
import com.zutubi.pulse.master.events.build.BuildRequestEvent;
import com.zutubi.pulse.master.events.build.SingleBuildRequestEvent;
import com.zutubi.pulse.master.model.*;
import com.zutubi.pulse.master.scm.LatestScmRevisionSupplier;
import com.zutubi.pulse.master.scm.ScmManager;
import com.zutubi.pulse.master.tove.config.project.triggers.DependentBuildTriggerConfiguration;
import com.zutubi.pulse.master.tove.config.project.triggers.TriggerUtils;
import com.zutubi.util.UnaryProcedure;
import com.zutubi.util.adt.TreeNode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The extended build request handler is responsible for handling all
 * non personal build requests.
 *
 * If the request is for a rebuild, then a set of upstream builds are
 * queued and configured accordingly.
 *
 * For all builds, the downstream builds that will be triggered by
 * successful builds is determined and produces queue requests.
 *
 * For all of the queue requests that are generated, each build will only
 * contain a single build for each project.  The queued request will be
 * configured to ensure that the ordering in which the builds are activated
 * is correct.
 */
public class ExtendedBuildRequestHandler extends BaseBuildRequestHandler
{
    private ScmManager scmManager;

    public List<QueuedRequest> prepare(final BuildRequestEvent request)
    {
        if (request.getMetaBuildId() != 0)
        {
            throw new IllegalArgumentException("The build request has already been handled by another handler.");
        }
        request.setMetaBuildId(getMetaBuildId());

        List<QueuedRequest> requestsToQueue = new LinkedList<QueuedRequest>();

        GraphBuilder builder = objectFactory.buildBean(GraphBuilder.class);
        GraphFilters filters = objectFactory.buildBean(GraphFilters.class);

        Project project = projectManager.getProject(request.getProjectId(), false);

        LinkedList<QueuedRequest> upstreamRequests = new LinkedList<QueuedRequest>();
        QueuedRequest upstreamRoot = null;
        if (request.getOptions().isRebuild())
        {
            // a rebuild indicates that we should build our upstream dependencies.
            TreeNode<BuildGraphData> upstream = builder.buildUpstreamGraph(project.getConfig(),
                    filters.status(request.getStatus()),
                    filters.transitive(),
                    filters.duplicate()
            );
            upstreamRequests = prepareUpstreamRequests(request, upstream);
            upstreamRoot = upstreamRequests.removeLast();
        }

        TreeNode<BuildGraphData> downstream = builder.buildDownstreamGraph(project.getConfig(), 
                filters.trigger(),
                filters.duplicate()
        );
        LinkedList<QueuedRequest> downstreamRequests = prepareDownstreamRequests(request, downstream);
        QueuedRequest downstreamRoot = downstreamRequests.removeFirst();

        QueuedRequest mergedRoot = (upstreamRoot != null) ? upstreamRoot : downstreamRoot;

        requestsToQueue.addAll(upstreamRequests);
        requestsToQueue.add(mergedRoot);
        requestsToQueue.addAll(downstreamRequests);

        return requestsToQueue;
    }

    private LinkedList<QueuedRequest> prepareDownstreamRequests(final BuildRequestEvent request, TreeNode<BuildGraphData> downstream)
    {
        final HashMap<Project, QueuedRequest> ownerRequests = new HashMap<Project, QueuedRequest>();
        final LinkedList<QueuedRequest> requestsToQueue = new LinkedList<QueuedRequest>();

        downstream.breadthFirstWalk(new UnaryProcedure<TreeNode<BuildGraphData>>()
        {
            public void run(TreeNode<BuildGraphData> node)
            {
                Project owner = getNodeProject(node);
                if (!ownerRequests.containsKey(owner))
                {
                    BuildRequestEvent newRequest = cloneAndRegisterIfNotOriginal(request, owner, false);

                    DependentBuildTriggerConfiguration trigger = TriggerUtils.getTrigger(owner.getConfig(), DependentBuildTriggerConfiguration.class);
                    if (trigger != null && !node.isRoot())
                    {
                        Project upstreamOwner = getNodeProject(node.getParent());
                        QueuedRequest upstreamRequest = ownerRequests.get(upstreamOwner);

                        TriggerOptions options = newRequest.getOptions();
                        if (trigger.isPropagateStatus())
                        {
                            options.setStatus(upstreamRequest.getRequest().getStatus());
                        }

                        if (trigger.isPropagateVersion())
                        {
                            options.setVersion(upstreamRequest.getRequest().getVersion());
                        }

                        BuildRevision upstreamRevision = upstreamRequest.getRequest().getRevision();
                        if (trigger.getRevisionHandling() == DependentBuildTriggerConfiguration.RevisionHandling.PROPAGATE_FROM_UPSTREAM)
                        {
                            newRequest.setRevision(upstreamRevision);
                        }
                        else if (trigger.getRevisionHandling() == DependentBuildTriggerConfiguration.RevisionHandling.FIX_WITH_UPSTREAM && !upstreamRevision.isInitialised())
                        {
                            upstreamRevision.addDependentRevision(newRequest.getRevision());
                        }
                    }

                    QueuedRequest queuedRequest = newQueuedRequest(newRequest);
                    requestsToQueue.add(queuedRequest);
                    ownerRequests.put(owner, queuedRequest);
                }

                if (!node.isRoot())
                {
                    QueuedRequest queuedRequest = ownerRequests.get(owner);
                    Project dependentProject = getNodeProject(node.getParent());
                    queuedRequest.getRequest().addDependentOwner(dependentProject);
                    queuedRequest.addPredicate(new DependencyCompleteQueuePredicate(buildQueue, dependentProject));
                }
            }
        });

        return requestsToQueue;
    }

    private Project getNodeProject(TreeNode<BuildGraphData> node)
    {
        return projectManager.getProject(node.getData().getProjectConfig().getProjectId(), true);
    }

    private LinkedList<QueuedRequest> prepareUpstreamRequests(final BuildRequestEvent request, final TreeNode<BuildGraphData> upstream)
    {
        final Map<Object, QueuedRequest> ownerRequests = new HashMap<Object, QueuedRequest>();
        final LinkedList<QueuedRequest> requestsToQueue = new LinkedList<QueuedRequest>();

        upstream.depthFirstWalk(new UnaryProcedure<TreeNode<BuildGraphData>>()
        {
            public void run(TreeNode<BuildGraphData> node)
            {
                Project owner = getNodeProject(node);

                if (!ownerRequests.containsKey(owner))
                {
                    BuildRequestEvent newRequest = cloneAndRegisterIfNotOriginal(request, owner, true);

                    // create queued request.
                    QueuedRequest request = newQueuedRequest(newRequest);
                    ownerRequests.put(owner, request);
                    requestsToQueue.add(request);
                }

                QueuedRequest queuedRequest = ownerRequests.get(owner);

                for (TreeNode<BuildGraphData> child : node.getChildren())
                {
                    Project childProject = getNodeProject(child);
                    queuedRequest.getRequest().addDependentOwner(childProject);
                    queuedRequest.addPredicate(new DependencyCompleteQueuePredicate(buildQueue, childProject));
                }
            }
        });

        upstream.breadthFirstWalk(new UnaryProcedure<TreeNode<BuildGraphData>>()
        {
            public void run(TreeNode<BuildGraphData> node)
            {
                Project owner = getNodeProject(node);
                QueuedRequest request = ownerRequests.get(owner);
                DependentBuildTriggerConfiguration trigger = TriggerUtils.getTrigger(owner.getConfig(), DependentBuildTriggerConfiguration.class);
                if (trigger != null)
                {
                    BuildRevision buildRevision = request.getRequest().getRevision();
                    boolean propagate = trigger.getRevisionHandling() == DependentBuildTriggerConfiguration.RevisionHandling.PROPAGATE_FROM_UPSTREAM;
                    boolean chain = trigger.getRevisionHandling() == DependentBuildTriggerConfiguration.RevisionHandling.FIX_WITH_UPSTREAM && !buildRevision.isInitialised();

                    if (propagate || chain)
                    {
                        for (TreeNode<BuildGraphData> child : node.getChildren())
                        {
                            Project childOwner = getNodeProject(child);
                            QueuedRequest childRequest = ownerRequests.get(childOwner);
                            if (propagate)
                            {
                                childRequest.getRequest().setRevision(buildRevision);
                            }
                            else
                            {
                                childRequest.getRequest().getRevision().addDependentRevision(buildRevision);
                            }
                        }
                    }
                }
            }
        });

        return requestsToQueue;
    }

    /**
     * Create a new build request event if the specified events owner is not the same as the
     * specified owner.
     *
     * @param originalRequest   the request to be conditionally 'cloned'.
     * @param owner             the owner of the new request
     * @param upstream          indicates whether or not the new build request is upstream of the original or not.
     * @return  a new request, or the old one if the owner is the same as the requests owner.
     */
    private BuildRequestEvent cloneAndRegisterIfNotOriginal(BuildRequestEvent originalRequest, Project owner, boolean upstream)
    {
        if (originalRequest.getOwner().equals(owner))
        {
            return originalRequest;
        }

        String originalOwner = originalRequest.getOwner().getName();

        TriggerOptions options = new TriggerOptions(originalRequest.getOptions());
        options.setReason((upstream) ? new RebuildBuildReason(originalOwner) : new DependencyBuildReason(originalOwner));
        BuildRevision revision = new BuildRevision(new LatestScmRevisionSupplier(owner, scmManager));
        BuildRequestEvent newRequest = new SingleBuildRequestEvent(this, owner, revision, options);
        newRequest.setMetaBuildId(originalRequest.getMetaBuildId());

        buildRequestRegistry.register(newRequest);
        return newRequest;
    }

    private QueuedRequest newQueuedRequest(BuildRequestEvent request)
    {
        List<QueuedRequestPredicate> defaultPredicates = new LinkedList<QueuedRequestPredicate>();
        Project project = (Project) request.getOwner();
        defaultPredicates.add(new ActiveBuildsPerOwnerPredicate(buildQueue, project.getConfig().getOptions().getConcurrentBuilds()));
        if (request.canJumpQueue())
        {
            defaultPredicates.add(new HeadOfOwnersCanBuildNowQueuePredicate(buildQueue));
        }
        else
        {
            defaultPredicates.add(new HeadOfOwnerQueuePredicate(buildQueue));
        }
        return new QueuedRequest(request, defaultPredicates);
    }

    public void setScmManager(ScmManager scmManager)
    {
        this.scmManager = scmManager;
    }
}
