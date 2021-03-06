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

package com.zutubi.tove.security;

/**
 * Interface for managing access to protected resources.  The manager
 * determines if a given actor has the authority to perform a given action on
 * a given resource.
 */
public interface AccessManager
{
    String ACTION_ADMINISTER = "administer";
    String ACTION_CREATE = "create";
    String ACTION_DELETE = "delete";
    String ACTION_VIEW = "view";
    String ACTION_WRITE = "write";

    /**
     * Returns the currently-applicable actor, or null if there is no such
     * actor.  For example, if the calling thread is "logged in" as a user,
     * that user is returned.
     *
     * @return the calling thread's actor
     */
    Actor getActor();

    /**
     * Determines if the given actor has permission to perform the given
     * action on the given resource.
     *
     * @param actor    the actor trying to perform the action
     * @param action   the action the actor wants to perform
     * @param resource the resource being acted on, may be null for a
     *                 "global" action
     * @return true iff the actor has permission to perform the action
     */
    boolean hasPermission(Actor actor, String action, Object resource);

    /**
     * Determines if the current actor has permission to perform the given
     * action on the given resource.
     *
     * @see #getActor()
     * @see #hasPermission(Actor, String, Object)
     *
     * @param action   the action the actor wants to perform
     * @param resource the resource being acted on, may be null for a
     *                 "global" action
     * @return true iff the actor has permission to perform the action
     */
    boolean hasPermission(String action, Object resource);

    /**
     * Ensures the given actor has permission to perform the given action on
     * the given resource, throwing an exception if they do not.
     *
     * @see #hasPermission(Actor, String, Object)
     *
     * @param actor    the actor trying to perform the action
     * @param action   the action the actor wants to perform
     * @param resource the resource being acted on, may be null for a
     *                 "global" action
     * @throws org.springframework.security.access.AccessDeniedException if permission is
     *         denied
     */
    void ensurePermission(Actor actor, String action, Object resource);

    /**
     * Ensures that the current actor has permission to perform the given
     * action on the given resource, throwing an exception if they do not.
     *
     * @see #getActor()
     * @see #ensurePermission(Actor, String, Object)
     *
     * @param action   the action the actor wants to perform
     * @param resource the resource being acted on, may be null for a
     *                 "global" action
     * @throws org.springframework.security.access.AccessDeniedException if permission is
     *         denied
     */
    void ensurePermission(String action, Object resource);
}
