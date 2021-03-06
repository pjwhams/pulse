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

package com.zutubi.pulse.master.hook.email;

import com.google.common.base.Predicate;
import com.zutubi.pulse.core.api.PulseException;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.pulse.core.model.PersistentChangelist;
import com.zutubi.pulse.core.scm.api.ScmCapability;
import com.zutubi.pulse.core.scm.api.ScmClient;
import com.zutubi.pulse.core.scm.api.ScmContext;
import com.zutubi.pulse.core.scm.api.ScmException;
import com.zutubi.pulse.core.scm.config.api.CommitterMappingConfiguration;
import com.zutubi.pulse.master.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.master.model.*;
import com.zutubi.pulse.master.notifications.NotificationAttachment;
import com.zutubi.pulse.master.notifications.ResultNotifier;
import com.zutubi.pulse.master.notifications.email.EmailService;
import com.zutubi.pulse.master.notifications.renderer.BuildResultRenderer;
import com.zutubi.pulse.master.notifications.renderer.RenderService;
import com.zutubi.pulse.master.notifications.renderer.RenderedResult;
import com.zutubi.pulse.master.scm.ScmClientUtils;
import com.zutubi.pulse.master.scm.ScmManager;
import com.zutubi.pulse.master.tove.config.admin.EmailConfiguration;
import com.zutubi.pulse.master.tove.config.admin.GlobalConfiguration;
import com.zutubi.pulse.master.tove.config.group.GroupConfiguration;
import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;
import com.zutubi.pulse.master.tove.config.project.ProjectContactsConfiguration;
import com.zutubi.pulse.master.tove.config.project.hooks.BuildHookTaskConfiguration;
import com.zutubi.pulse.master.tove.config.project.hooks.CompatibleHooks;
import com.zutubi.pulse.master.tove.config.project.hooks.ManualBuildHookConfiguration;
import com.zutubi.pulse.master.tove.config.project.hooks.PostBuildHookConfiguration;
import com.zutubi.pulse.master.tove.config.user.UserConfiguration;
import com.zutubi.pulse.master.tove.config.user.contacts.ContactConfiguration;
import com.zutubi.pulse.master.tove.config.user.contacts.EmailContactConfiguration;
import com.zutubi.tove.annotations.*;
import com.zutubi.tove.config.ConfigurationProvider;
import com.zutubi.tove.config.api.AbstractConfiguration;
import com.zutubi.util.StringUtils;
import com.zutubi.util.logging.Logger;
import com.zutubi.validation.annotations.Numeric;
import com.zutubi.validation.annotations.Required;

import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.find;
import static java.util.Arrays.asList;

/**
 * A build hook task that sends email notifications to project contacts and/or
 * users that committed changes that affected the build.
 */
@SymbolicName("zutubi.sendEmailTaskConfig")
@Form(fieldOrder = {"template", "attachLogs", "logLineLimit", "emailContacts", "emailCommitters", "includeUpstreamCommitters", "emailDomain", "sinceLastSuccess", "ignorePulseUsers", "useScmEmails", "enforceDomain"})
@CompatibleHooks({ManualBuildHookConfiguration.class, PostBuildHookConfiguration.class})
@Wire
public class SendEmailTaskConfiguration extends AbstractConfiguration implements BuildHookTaskConfiguration
{
    public static final Logger LOG = Logger.getLogger(SendEmailTaskConfiguration.class);

    @Required @Dropdown(optionProvider = "com.zutubi.pulse.master.tove.config.user.SubscriptionTemplateOptionProvider")
    private String template;
    @ControllingCheckbox(checkedFields = "logLineLimit")
    private boolean attachLogs;
    @Numeric(min = 0, max = 250)
    private int logLineLimit = 50;
    private boolean emailContacts;
    @ControllingCheckbox(checkedFields = {"includeUpstreamCommitters", "emailDomain", "sinceLastSuccess", "ignorePulseUsers", "useScmEmails", "enforceDomain"})
    private boolean emailCommitters;
    private boolean includeUpstreamCommitters;
    @Required
    private String emailDomain;
    private boolean sinceLastSuccess = false;
    private boolean ignorePulseUsers = false;
    private boolean useScmEmails = false;
    private boolean enforceDomain = false;

    private BuildResultRenderer buildResultRenderer;
    private ConfigurationProvider configurationProvider;
    private BuildManager buildManager;
    private ChangelistManager changelistManager;
    private UserManager userManager;
    private ScmManager scmManager;
    private EmailService emailService;
    private RenderService renderService;
    private MasterConfigurationManager configurationManager;

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }

    public boolean isAttachLogs()
    {
        return attachLogs;
    }

    public void setAttachLogs(boolean attachLogs)
    {
        this.attachLogs = attachLogs;
    }

    public int getLogLineLimit()
    {
        return logLineLimit;
    }

    public void setLogLineLimit(int logLineLimit)
    {
        this.logLineLimit = logLineLimit;
    }

    public boolean isEmailContacts()
    {
        return emailContacts;
    }

    public void setEmailContacts(boolean emailContacts)
    {
        this.emailContacts = emailContacts;
    }

    public boolean isEmailCommitters()
    {
        return emailCommitters;
    }

    public boolean isIncludeUpstreamCommitters()
    {
        return includeUpstreamCommitters;
    }

    public void setIncludeUpstreamCommitters(boolean includeUpstreamCommitters)
    {
        this.includeUpstreamCommitters = includeUpstreamCommitters;
    }

    public void setEmailCommitters(boolean emailCommitters)
    {
        this.emailCommitters = emailCommitters;
    }

    public String getEmailDomain()
    {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain)
    {
        this.emailDomain = emailDomain;
    }

    public boolean isSinceLastSuccess()
    {
        return sinceLastSuccess;
    }

    public void setSinceLastSuccess(boolean sinceLastSuccess)
    {
        this.sinceLastSuccess = sinceLastSuccess;
    }

    public boolean isUseScmEmails()
    {
        return useScmEmails;
    }

    public void setUseScmEmails(boolean useScmEmails)
    {
        this.useScmEmails = useScmEmails;
    }

    public boolean isEnforceDomain()
    {
        return enforceDomain;
    }

    public void setEnforceDomain(boolean enforceDomain)
    {
        this.enforceDomain = enforceDomain;
    }

    public boolean isIgnorePulseUsers()
    {
        return ignorePulseUsers;
    }

    public void setIgnorePulseUsers(boolean ignorePulseUsers)
    {
        this.ignorePulseUsers = ignorePulseUsers;
    }

    public void execute(ExecutionContext context, BuildResult buildResult, RecipeResultNode resultNode, boolean onAgent) throws Exception
    {
        if (!buildResult.completed())
        {
            throw new PulseException("Email build hook task can only be applied post-build");
        }

        EmailConfiguration emailConfiguration = configurationProvider.get(EmailConfiguration.class);
        if (!StringUtils.stringSet(emailConfiguration.getHost()))
        {
            throw new PulseException("Cannot execute email build hook task as no SMTP host is configured.");
        }

        ProjectConfiguration projectConfig = configurationProvider.getAncestorOfType(this, ProjectConfiguration.class);
        Set<String> emails = new HashSet<String>();
        if (emailContacts)
        {
            addContactEmails(projectConfig, emails);
        }

        if (emailCommitters)
        {
            addCommitterEmails(projectConfig, buildResult.getProject().getState(), getBuilds(buildResult), emails);
        }

        if (emails.size() > 0)
        {
            buildResult.loadFailedTestResults(configurationManager.getDataDirectory(), ResultNotifier.getFailureLimit());

            GlobalConfiguration globalConfiguration = configurationProvider.get(GlobalConfiguration.class);
            RenderedResult rendered = renderService.renderResult(buildResult, globalConfiguration.getBaseUrl(), template);
            String mimeType = buildResultRenderer.getTemplateInfo(template, buildResult.isPersonal()).getMimeType();
            String subject = rendered.getSubject();

            MimeMultipart message = new MimeMultipart();
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(rendered.getContent(), mimeType);
            message.addBodyPart(bodyPart);

            for (NotificationAttachment attachment: renderService.getAttachments(buildResult, attachLogs, logLineLimit, false))
            {
                message.addBodyPart(attachment.asBodyPart());
            }

            emailService.queueMail(emails, subject, message, emailConfiguration);
        }
    }

    private void addContactEmails(ProjectConfiguration projectConfig, Set<String> emails)
    {
        LOG.debug("Adding contact emails for project '" + projectConfig.getName() + "'");
        ProjectContactsConfiguration contacts = projectConfig.getContacts();
        for (GroupConfiguration group: contacts.getGroups())
        {
            for (UserConfiguration user: userManager.getGroupMembers(group))
            {
                addContactEmail(user, emails);
            }
        }

        for (UserConfiguration user: contacts.getUsers())
        {
            addContactEmail(user, emails);
        }
    }

    private void addContactEmail(UserConfiguration user, Set<String> emails)
    {
        ContactConfiguration primaryContact = user.getPrimaryContact();
        if (primaryContact != null && primaryContact instanceof EmailContactConfiguration)
        {
            emails.add(((EmailContactConfiguration) primaryContact).getAddress());
        }
        else
        {
            LOG.debug("Ignoring contact user '" + user.getName() + "' because there is no primary contact email");
        }
    }

    private List<BuildResult> getBuilds(BuildResult result)
    {
        if (sinceLastSuccess)
        {
            BuildResult previousSuccess = buildManager.getPreviousBuildResultWithRevision(result, ResultState.getHealthyStates());
            long lowestNumber = previousSuccess == null ? 1 : previousSuccess.getNumber() + 1;
            List<BuildResult> builds = buildManager.queryBuilds(result.getProject(), ResultState.getCompletedStates(), lowestNumber, result.getNumber() - 1, 0, -1, false, false);
            builds.add(result);
            return builds;
        }
        else
        {
            return asList(result);
        }
    }

    private void addCommitterEmails(ProjectConfiguration projectConfig, Project.State projectState, List<BuildResult> builds, Set<String> emails)
    {
        LOG.debug("Adding committer emails for project '" + projectConfig.getName() + "' (ignorePulseUsers = " + ignorePulseUsers + ")");
        Set<String> seenLogins = new HashSet<String>();
        for (BuildResult build: builds)
        {
            List<PersistentChangelist> changelists = changelistManager.getChangesForBuild(build, 0, true);
            if (includeUpstreamCommitters)
            {
                LOG.debug("Including upstream committers");
                BuildResult sinceBuild = buildManager.getPreviousBuildResult(build);
                List<UpstreamChangelist> upstreamChangelists = changelistManager.getUpstreamChangelists(build, sinceBuild);
                changelists.addAll(transform(upstreamChangelists, new UpstreamChangelist.ToChangelistFunction()));
            }

            for (PersistentChangelist change : changelists)
            {
                String scmLogin = change.getAuthor();
                // Only bother to map and add if we haven't already done so.
                if (seenLogins.add(scmLogin))
                {
                    LOG.debug("Processing author '" + scmLogin + "'");
                    if (StringUtils.stringSet(scmLogin) && (!ignorePulseUsers || userManager.getUser(scmLogin) == null))
                    {
                        String email = getEmail(projectConfig, projectState, scmLogin);
                        if (checkDomain(email))
                        {
                            emails.add(email);
                        }
                        else
                        {
                            LOG.debug("Ignoring email '" + email + "' because it does not have domain '" + emailDomain + "'");
                        }
                    }
                }
            }
        }
    }

    private boolean checkDomain(String email)
    {
        if (enforceDomain)
        {
            String domain = emailDomain;
            if (!domain.startsWith("@"))
            {
                domain = "@" + domain;
            }

            return email.endsWith(domain);
        }
        else
        {
            return true;
        }
    }

    private String getEmail(ProjectConfiguration projectConfig, Project.State projectState, final String scmLogin)
    {
        CommitterMappingConfiguration mapping = find(projectConfig.getScm().getCommitterMappings(), new Predicate<CommitterMappingConfiguration>()
        {
            public boolean apply(CommitterMappingConfiguration committerMappingConfiguration)
            {
                return committerMappingConfiguration.getScmLogin().equals(scmLogin);
            }
        }, null);

        String email = null;
        if (mapping == null)
        {
            if (useScmEmails)
            {
                LOG.debug("Looking up email from SCM");
                try
                {
                    email = ScmClientUtils.withScmClient(projectConfig, projectState, scmManager, new ScmClientUtils.ScmContextualAction<String>()
                    {
                        public String process(ScmClient client, ScmContext context) throws ScmException
                        {
                            if (client.getCapabilities(context).contains(ScmCapability.EMAIL))
                            {
                                return client.getEmailAddress(context, scmLogin);
                            }
                            else
                            {
                                return null;
                            }
                        }
                    });
                }
                catch (ScmException e)
                {
                    // Oh well, fall back to guess.
                    LOG.info("Failed to lookup email for user '" + scmLogin + "': " + e.getMessage(), e);
                }
            }

            if (email == null)
            {
                email = scmLogin;
            }
        }
        else
        {
            LOG.debug("Using configured mapping to email '" + mapping.getEmail() + "'");
            email = mapping.getEmail();
        }

        if (email.contains("@"))
        {
            return email;
        }
        else
        {
            return StringUtils.join("@", true, email, emailDomain);
        }
    }

    public void setBuildResultRenderer(BuildResultRenderer buildResultRenderer)
    {
        this.buildResultRenderer = buildResultRenderer;
    }

    public void setConfigurationProvider(ConfigurationProvider configurationProvider)
    {
        this.configurationProvider = configurationProvider;
    }

    public void setBuildManager(BuildManager buildManager)
    {
        this.buildManager = buildManager;
    }

    public void setChangelistManager(ChangelistManager changelistManager)
    {
        this.changelistManager = changelistManager;
    }

    public void setUserManager(UserManager userManager)
    {
        this.userManager = userManager;
    }

    public void setScmManager(ScmManager scmManager)
    {
        this.scmManager = scmManager;
    }

    public void setEmailService(EmailService emailService)
    {
        this.emailService = emailService;
    }

    public void setRenderService(RenderService renderService)
    {
        this.renderService = renderService;
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }
}

