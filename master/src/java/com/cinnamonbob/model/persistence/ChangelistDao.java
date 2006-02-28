package com.cinnamonbob.model.persistence;

import com.cinnamonbob.core.model.Changelist;
import com.cinnamonbob.model.User;

import java.util.List;

/**
 * DAO for accessing Changelist objects.
 */
public interface ChangelistDao extends EntityDao<Changelist>
{
    /**
     * Returns a list of up to max changelists submitted by the given user.
     *
     * @param user the user to restrict the query to
     * @param max  the maximum number of changelists to return
     * @return a list of the latest changes by the user
     */
    List<Changelist> findLatestByUser(User user, int max);
}
