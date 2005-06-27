package com.cinnamonbob.model;

import com.cinnamonbob.core2.BuildResult;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import java.util.logging.Logger;
import java.util.Date;
import java.io.StringWriter;

/**
 * 
 *
 */
public class EmailContactPoint implements ContactPoint
{
    private static final Logger LOG = Logger.getLogger(EmailContactPoint.class.getName());
    
    private String          name;
    private InternetAddress address;
    
    
    public EmailContactPoint(String name)
    {
        this.name = name;
    }

    /* (non-Javadoc)
     * @see com.cinnamonbob.core.ContactPoint#getName()
     */
    public String getName()
    {
        return name;
    }

    /* (non-Javadoc)
     * @see com.cinnamonbob.core.ContactPoint#notify(com.cinnamonbob.core.BuildResult)
     */
    public void notify(BuildResult result)
    {
        String subject = "[CiB] " + result.getProjectName() + ": Build " + Integer.toString(result.getId()) + (result.succeeded() ? " succeeded" : " failed");
        sendMail(subject, renderResult(result));
    }
    
    private String renderResult(BuildResult result)
    {
        StringWriter w = new StringWriter();
        // TODO renderer should come from elsewhere
        VelocityBuildResultRenderer renderer = new VelocityBuildResultRenderer();
        renderer.render(result, BuildResultRenderer.TYPE_PLAIN, w);
        return  w.toString();
    }
    
    private void sendMail(String subject, String body)
    {
//        SMTPService smtp = (SMTPService)theBuilder.lookupService(SMTPService.SERVICE_NAME);
//        
//        if(smtp == null)
//        {
//            // TODO detect this badness in config somehow
//            LOG.warning("Could not locate SMTP service to send email notifications.");
//            return;
//        }
//        
//        try
//        {
//            Session session = smtp.getSession();
//            
//            Message msg = new MimeMessage(session);
//            msg.setFrom(smtp.getFromAddress());
//            msg.setRecipient(Message.RecipientType.TO, address);
//            msg.setSubject(subject);
//            msg.setText(body);
//            msg.setHeader("X-Mailer", "Project-Cinnamon");
//            msg.setSentDate(new Date());
//            
//            Transport.send(msg);
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//        }
    }
    
    
}
