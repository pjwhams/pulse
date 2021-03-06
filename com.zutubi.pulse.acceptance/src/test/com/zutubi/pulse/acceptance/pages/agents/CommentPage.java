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

package com.zutubi.pulse.acceptance.pages.agents;

import com.zutubi.pulse.acceptance.pages.ConfirmDialog;

/**
 * Abstraction over pages that support adding, showing and deleting comments.
 */
public interface CommentPage
{
    /**
     * Opens the page in the browser and waits for it to be ready.
     */
    void openAndWaitFor();

    /**
     * Waits for the list of comments to appear.
     *
     * @param timeout maximum number of milliseconds to wait
     */
    void waitForComments(long timeout);

    /**
     * Indicates if the list of comments is present.  There must be at least
     * one comment for the list to appear.
     *
     * @return true if the comment list ir present, false if not
     */
    boolean isCommentsPresent();

    /**
     * Tests if a comment is present.
     *
     * @param commentId unique id of the comment (as returned by the remote API)
     * @return true if the given comment is present, false if not
     */
    boolean isCommentPresent(long commentId);

    /**
     * Indicates if the delete link is present for a comment.  The user must
     * have delete permission for this link to appear.
     *
     * @param commentId unique id of the comment (as returned by the remote API)
     * @return true if the delete link is present, false if not
     */
    boolean isCommentDeleteLinkPresent(long commentId);

    /**
     * Clicks the delete link for a comment, returning the dialog that should
     * pop up.
     *
     * @param commentId unique id of the comment (as returned by the remote API)
     * @return the dialog popped to confirm deletion
     */
    ConfirmDialog clickDeleteComment(long commentId);

    /**
     * Clicks an action link (e.g. for initiating a comment).
     *
     * @param actionName name of the action to click
     */
    void clickAction(String actionName);
}
