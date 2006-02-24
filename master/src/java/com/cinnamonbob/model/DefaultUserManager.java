package com.cinnamonbob.model;

import com.cinnamonbob.model.persistence.ContactPointDao;
import com.cinnamonbob.model.persistence.UserDao;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

import java.util.List;

/**
 * 
 *
 */
public class DefaultUserManager implements UserManager
{
    private UserDao userDao;
    private ContactPointDao contactDao;

    public void setUserDao(UserDao userDao)
    {
        this.userDao = userDao;
    }

    public void setContactPointDao(ContactPointDao contactDao)
    {
        this.contactDao = contactDao;
    }

    public void save(User user)
    {
        userDao.save(user);
    }

    public void save(ContactPoint contact)
    {
        contactDao.save(contact);
    }

    public User getUser(String login)
    {
        return userDao.findByLogin(login);
    }

    public User getUser(long id)
    {
        return userDao.findById(id);
    }

    public List<User> getUsersWithLoginLike(String login)
    {
        return userDao.findByLikeLogin(login);
    }

    public List<User> getAllUsers()
    {
        return userDao.findAll();
    }

    public ContactPoint getContactPoint(long id)
    {
        return contactDao.findById(id);
    }

    public void delete(User user)
    {
        userDao.delete(user);
    }

    public void delete(ContactPoint contact)
    {
        contactDao.delete(contact);
    }

    public int getUserCount()
    {
        return userDao.getUserCount();
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException
    {
        UserDetails details = userDao.findByLogin(username);
        if (details == null)
        {
            throw new UsernameNotFoundException("Unknown user");
        }

        return details;
    }
}
