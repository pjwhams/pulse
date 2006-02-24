package com.cinnamonbob.model;

import org.acegisecurity.userdetails.UserDetailsService;

import java.util.List;

/**
 * 
 *
 */
public interface UserManager extends EntityManager<User>, UserDetailsService
{
    /**
     * Retrieve an instance of the user identified by the login name
     * @param login
     *
     * @return a user instance, or null if no matching user is found.
     */
    User getUser(String login);

    /**
     * Retrieve an instance of the user identified by the unique id.
     * @param id
     *
     * @return a user instance, or null if no matching user is found.
     */
    User getUser(long id);
    
    List<User> getUsersWithLoginLike(String name);
    List<User> getAllUsers();

    
    ContactPoint getContactPoint(long id);
    void delete(ContactPoint contact);

    /**
     * Return the number of users configured in the system.
     *
     */
    int getUserCount();
}
