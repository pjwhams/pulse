/********************************************************************************
 @COPYRIGHT@
 ********************************************************************************/
package com.zutubi.pulse.jabber;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.bootstrap.ApplicationConfiguration;
import com.zutubi.pulse.bootstrap.ConfigurationManager;
import com.zutubi.pulse.core.Stoppable;
import com.zutubi.pulse.util.logging.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;

/**
 */
public class JabberManager implements Stoppable, PacketListener
{
    public static final int DEFAULT_PORT = 5222;

    private static final Logger LOG = Logger.getLogger(JabberManager.class);

    private XMPPConnection connection = null;
    private ConfigurationManager configurationManager;

    /**
     * Holds the status from when we last tried to connect.  Will be null
     * if everything was ok.
     */
    private String statusMessage = null;

    public void init()
    {
        statusMessage = null;
        ApplicationConfiguration appConfig = configurationManager.getAppConfig();
        if (TextUtils.stringSet(appConfig.getJabberHost()))
        {
            LOG.info("Initialising Jabber");
            Roster.setDefaultSubscriptionMode(Roster.SUBSCRIPTION_ACCEPT_ALL);
            try
            {
                openConnection(appConfig);
            }
            catch (Exception e)
            {
                // Workaround for SSL negotiation problems: try once more!
                LOG.warning("Retrying Jabber initialisation");
                try
                {
                    stop(true);
                    openConnection(appConfig);
                }
                catch(Exception nesty)
                {
                    stop(true);
                    statusMessage = "Could not initialise Jabber: " + nesty.getMessage();
                    LOG.error(statusMessage, nesty);
                }
            }
        }

        if(connection != null)
        {
            LOG.info("Jabber initialised");
        }
    }

    private void openConnection(ApplicationConfiguration appConfig)
            throws XMPPException
    {
        if(appConfig.getJabberForceSSL())
        {
            connection = new SSLXMPPConnection(appConfig.getJabberHost(), appConfig.getJabberPort());
        }
        else
        {
            connection = new XMPPConnection(appConfig.getJabberHost(), appConfig.getJabberPort());
        }
        
        connection.login(appConfig.getJabberUsername(), appConfig.getJabberPassword());
        connection.addPacketListener(this, new MessageTypeFilter(Message.Type.ERROR));
    }

    public XMPPConnection getConnection()
    {
        return connection;
    }

    public void stop(boolean force)
    {
        if(connection != null)
        {
            try
            {
                connection.close();
            }
            catch (Exception e)
            {
                // Ignore
            }
            connection = null;
        }
    }

    public void processPacket(Packet packet)
    {
        XMPPError error = packet.getError();
        if(error != null)
        {
            String message = "Jabber error";

            if(packet.getFrom()  != null)
            {
                message += ": from " + packet.getFrom();
            }

            message += ": code " + error.getCode();

            if(error.getMessage()  != null)
            {
                message += ": " + error.getMessage();
            }

            LOG.error(message);
        }
    }

    public void refresh()
    {
        stop(true);
        init();
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public void setConfigurationManager(ConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }
}
