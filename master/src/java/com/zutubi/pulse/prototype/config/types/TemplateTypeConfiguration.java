package com.zutubi.pulse.prototype.config.types;

import com.zutubi.pulse.bootstrap.ComponentContext;
import com.zutubi.pulse.core.BuildException;
import com.zutubi.pulse.core.model.Revision;
import com.zutubi.pulse.personal.PatchArchive;
import com.zutubi.pulse.prototype.config.ProjectConfiguration;
import com.zutubi.pulse.util.FileSystemUtils;
import com.zutubi.util.logging.Logger;
import com.zutubi.config.annotations.Select;
import com.zutubi.config.annotations.Transient;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 *
 */
public abstract class TemplateTypeConfiguration extends TypeConfiguration
{
    private static final Logger LOG = Logger.getLogger(TemplateTypeConfiguration.class);

    private List<String> postProcessors = new LinkedList<String>();

    private List<ArtifactConfiguration> artifacts = new LinkedList<ArtifactConfiguration>();

    @Transient
    private VelocityEngine velocityEngine;

    @Select(optionProvider = "PostProcessorOptionProvider")
    public List<String> getPostProcessors()
    {
        return postProcessors;
    }

    public void setPostProcessors(List<String> postProcessors)
    {
        this.postProcessors = postProcessors;
    }

    public List<ArtifactConfiguration> getArtifacts()
    {
        return artifacts;
    }

    public void setArtifacts(List<ArtifactConfiguration> artifacts)
    {
        this.artifacts = artifacts;
    }

    public String getPulseFile(long id, ProjectConfiguration projectConfig, Revision revision, PatchArchive patch)
    {
        try
        {
            VelocityContext context = new VelocityContext();
            addPostProcessors(context);
            context.put("details", this);
            context.put("outputProcessors", postProcessors);
            context.put("artifacts", artifacts);
            setupContext(context);
            StringWriter stringWriter = new StringWriter(1024);
            getVelocityEngine().mergeTemplate("pulse-file" + File.separatorChar + getTemplateName(), context, stringWriter);
            return stringWriter.getBuffer().toString();
        }
        catch (Exception e)
        {
            LOG.warning(e);
            throw new BuildException("Loading template pulse file: " + e.getMessage(), e);
        }
    }

    protected abstract String getTemplateName();

    protected abstract void setupContext(VelocityContext context);

    public String getReference(String name)
    {
        // Help out velocity, which appears to be completely retarded when it
        // comes to escaping.
        return "${" + name + "}";
    }

    public VelocityEngine getVelocityEngine()
    {
        if(velocityEngine == null)
        {
            velocityEngine = (VelocityEngine) ComponentContext.getBean("velocityEngine");
        }
        return velocityEngine;
    }

    public void setVelocityEngine(VelocityEngine velocityEngine)
    {
        this.velocityEngine = velocityEngine;
    }

    private void addPostProcessors(VelocityContext context)
    {
        Set<String> includedProcessors = new TreeSet<String>();
        List<String> templates = new LinkedList<String>();

        for(String processor: postProcessors)
        {
            addProcessor(includedProcessors, processor, templates);
        }

        for(ArtifactConfiguration artifact: artifacts)
        {
            for(String processor: artifact.getPostprocessors())
            {
                addProcessor(includedProcessors, processor, templates);
            }
        }

        context.put("postProcessorTemplates", templates);
    }

    private void addProcessor(Set<String> includedProcessors, String processor, List<String> templates)
    {
        if(!includedProcessors.contains(processor))
        {
            includedProcessors.add(processor);
            templates.add(FileSystemUtils.composeFilename("pulse-file", "post-processors", processor + ".vm"));
        }
    }
}
