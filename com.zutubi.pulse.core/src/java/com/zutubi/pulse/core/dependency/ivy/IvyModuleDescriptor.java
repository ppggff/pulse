package com.zutubi.pulse.core.dependency.ivy;

import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.util.StringUtils;
import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * The ivy module descriptor provides a wrapper around ivy's ModuleDescriptor and
 * provides some utility methods to simplify the common types of interactions.
 *
 * One of the primary functions of the ivy module descriptor is handling the
 * necessary encoding of the ivy descriptor.  To that end, all data available via
 * the ivy module descriptor is in its decoded for.  When accessing the underlying
 * module descriptor directly, the data will be in its encoded form.
 */
public class IvyModuleDescriptor
{
    /**
     * The underlying module descriptor.  All of the data/configuration contained within this
     * descriptor has been encoded.
     */
    private DefaultModuleDescriptor descriptor;

    /**
     * The ivy system configuration.
     */
    private IvyConfiguration configuration;

    public static final String NAMESPACE_EXTRA_ATTRIBUTES = "e:";

    public static final String NAME_GLUE = ",";
    public static final String ALL_STAGES = "*";
    public static final String CORRESPONDING_STAGE = "#";
    public static final String SOURCEFILE = "sourcefile";
    public static final String STAGE = "stage";

    /**
     * The stage extra attribute is used to track which stage a particular artifact was produced by.
     */
    public static final String EXTRA_ATTRIBUTE_STAGE = NAMESPACE_EXTRA_ATTRIBUTES + STAGE;

    /**
     * The source file extra attribute is used to track the original artifact source file, simplifying
     * the process of publishing artifacts.
     */
    public static final String EXTRA_ATTRIBUTE_SOURCE_FILE = NAMESPACE_EXTRA_ATTRIBUTES + SOURCEFILE;

    /**
     * The build number extra attribute is a record of build number of the Pulse build that is
     * described by this module descriptor.
     */
    public static final String EXTRA_INFO_BUILD_NUMBER = NAMESPACE_EXTRA_ATTRIBUTES + "buildNumber";

    /**
     * Maps to a comma-separated list of module names for upstream projects on which our
     * dependency is optional.  If we fail to find a configuration in an optional dependency
     * it is not considered fatal.
     */
    public static final String EXTRA_INFO_OPTIONAL = NAMESPACE_EXTRA_ATTRIBUTES + "optional";

    /**
     * If there is no build number associated with a module descriptor, this value will be returned
     * when asking for the build number.
     *
     * @see #getBuildNumber() 
     */
    public static final long UNKNOWN_BUILD_NUMBER = -1;

    /**
     * Load the module descriptor defined by the contents of the specified file.
     *
     * Note, it is assumes that the contents of the file have been encoded according to the rules defined
     * within this class.
     *
     * @param file              the file that contains the xml format of an ivy module descriptor.
     * @param configuration     the ivy configuration
     *
     * @return  a new instance of the IvyModuleDescriptor.
     *
     * @throws Exception is thrown if there are any problems loading the descriptor for the file.
     */
    public static IvyModuleDescriptor newInstance(File file, IvyConfiguration configuration) throws Exception
    {
        return newInstance(file.toURI().toURL(), configuration);
    }

    /**
     * Load the module descriptor from the url.
     *
     * Note, it is assumes that the contents of the url have been encoded according to the rules defined
     * within this class.
     *
     * @param url               the url refering to an xml formatted module descriptor.
     * @param configuration     the ivy configuration
     *
     * @return  a new instance of the IvyModuleDescriptor
     *
     * @throws Exception on error.
     */
    public static IvyModuleDescriptor newInstance(URL url, IvyConfiguration configuration) throws Exception
    {
        ModuleDescriptorParser parser =  XmlModuleDescriptorParser.getInstance();

        IvySettings ivySettings = configuration.loadSettings();

        return new IvyModuleDescriptor((DefaultModuleDescriptor) parser.parseDescriptor(ivySettings, url, ivySettings.doValidate()), configuration);
    }

    /**
     * Create a new ivy module descriptor backed by the already defined descriptor and configuration.
     *
     * Note, it is assumes that the data within the descriptor have been encoded according to the rules defined
     * within this class.
     *
     * @param descriptor        the underlying ivy descriptor to be used by this wrapper
     * @param configuration     the ivy configuration
     */
    public IvyModuleDescriptor(DefaultModuleDescriptor descriptor, IvyConfiguration configuration)
    {
        this.descriptor = descriptor;
        this.configuration = configuration;
    }

    /**
     * Create a new ivy module descriptor.
     *
     * @param org               the organisation for the new descriptor
     * @param module            the module for the new descriptor
     * @param revision          the revision for the new descriptor
     * @param configuration     the ivy configuration
     */
    public IvyModuleDescriptor(String org, String module, String revision, IvyConfiguration configuration)
    {
        this(ModuleRevisionId.newInstance(org, module, revision), configuration);
    }

    /**
     * Create a new ivy module descriptor.
     *
     * @param org               the organisation for the new descriptor
     * @param module            the module for the new descriptor
     * @param revision          the revision for the new descriptor
     * @param status            the status for the new descriptor
     * @param configuration     the ivy configuration
     *
     * @see IvyStatus
     */
    public IvyModuleDescriptor(String org, String module, String revision, String status, IvyConfiguration configuration)
    {
        this(ModuleRevisionId.newInstance(org, module, revision), status, configuration);
    }

    /**
     * Create a new ivy module descriptor.
     *
     * @param mrid              the module revision id for the new descriptor
     * @param configuration     the ivy configuration
     */
    public IvyModuleDescriptor(ModuleRevisionId mrid, IvyConfiguration configuration)
    {
        this(mrid, IvyStatus.STATUS_INTEGRATION, configuration);
    }

    /**
     * Create a new ivy module descriptor.
     *
     * @param mrid              the module revision id for the new descriptor
     * @param status            the status for the new descriptor
     * @param configuration     the ivy configuration
     *
     * @see IvyStatus
     */
    public IvyModuleDescriptor(ModuleRevisionId mrid, String status, IvyConfiguration configuration)
    {
        descriptor = new DefaultModuleDescriptor(IvyEncoder.encode(mrid), status, null);
        descriptor.addExtraAttributeNamespace(NAMESPACE_EXTRA_ATTRIBUTES, "http://ant.apache.org/ivy/extra");
        this.configuration = configuration;
    }

    /**
     * Add an artifact to this module descriptor, using the artifact files name and extension as the
     * name and type of the artifact.  In the case that the filename does not have a name or extension, an
     * exception is thrown.
     *
     * The artifactFile and the stageName are added to an extraAttributes map as the
     * {@link #EXTRA_ATTRIBUTE_SOURCE_FILE} and {@link #EXTRA_ATTRIBUTE_STAGE} values respectively.
     *
     * @param artifactFile      the file representing the artifact to be added to this descriptor. The name
     * and extension of the artifact are taken from the respective portions of the file.
     * @param stageName          the stage to which this artifact belongs.
     *
     * @see #addArtifact(String, String, String, java.util.Map)
     * @see #EXTRA_ATTRIBUTE_SOURCE_FILE
     * @see #EXTRA_ATTRIBUTE_STAGE 
     */
    public void addArtifact(File artifactFile, String stageName)
    {
        String artifactName = artifactFile.getName().substring(0, artifactFile.getName().lastIndexOf('.'));
        String artifactExt = artifactFile.getName().substring(artifactFile.getName().lastIndexOf('.') + 1);

        addArtifact(artifactName, artifactExt, artifactFile, stageName);
    }

    /**
     * Add an artifact to this module descriptor, using the specified details.
     *
     * The artifactFile and the stageName are added to an extraAttributes map as the
     * {@link #EXTRA_ATTRIBUTE_SOURCE_FILE} and {@link #EXTRA_ATTRIBUTE_STAGE} values respectively.
     *
     * @param artifactName      the name of the artifact
     * @param artifactExt       the extension of the artifact.  This doubles up with the type of the artifact.
     * @param artifactFile      the actual artifact source file.
     * @param stageName         the name of the stage the artifact is associated with.
     */
    public void addArtifact(String artifactName, String artifactExt, File artifactFile, String stageName)
    {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        extraAttributes.put(EXTRA_ATTRIBUTE_SOURCE_FILE, getFilePathBestEffort(artifactFile));
        extraAttributes.put(EXTRA_ATTRIBUTE_STAGE, stageName);

        addArtifact(artifactName, artifactExt, stageName, extraAttributes);
    }

    /**
     * Add an artifact to this module descriptor.
     *
     * @param artifactName      the name of the artifact.
     * @param artifactExt       the extension of the artifact
     * @param stageName         the stage to which this artifact belongs.
     * @param extraAttributes   any extra attributes to be associated with this artifact.
     */
    public void addArtifact(String artifactName, String artifactExt, String stageName, Map<String, String> extraAttributes)
    {
        MDArtifact ivyArtifact = new MDArtifact(descriptor, artifactName, artifactExt, artifactExt, null, extraAttributes);
        ivyArtifact.addConfiguration(stageName);

        String conf = ensureConfigurationExists(stageName);

        descriptor.addArtifact(conf, IvyEncoder.encode(ivyArtifact));
    }

    /**
     * Add a new dependency to this module descriptor.  The transitive setting
     * of this dependency is false.
     *
     * @param mrid          the module revision id defining the dependency.
     * @param conf          the local configuration that this dependency belongs to
     * @param stageNames    the dependent modules stage mappings.  If not defined,
     * '*' is used to refer to all stages.
     */
    public void addDependency(ModuleRevisionId mrid, String conf, String... stageNames)
    {
        addDependency(mrid, conf, false, stageNames);
    }

    /**
     * Add a new dependency to this module descriptor.
     *
     * @param mrid          the module revision id defining the dependency
     * @param conf          the local configuration that this dependency belongs to
     * @param transitive    indicating whether or not the dependency is transitive
     * @param stageNames    the remote module stage mappings. If not defined,
     */
    public void addDependency(ModuleRevisionId mrid, String conf, boolean transitive, String... stageNames)
    {
        String masterConf = ensureConfigurationExists(conf);

        String dependencyConf = (stageNames.length > 0) ? StringUtils.join(NAME_GLUE, stageNames) : ALL_STAGES;
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(descriptor, mrid, true, false, transitive);
        dependencyDescriptor.addDependencyConfiguration(masterConf, dependencyConf);
        descriptor.addDependency(IvyEncoder.encode(dependencyDescriptor));
    }

    /**
     * Get the build number stored with this module descriptor, or {@link #UNKNOWN_BUILD_NUMBER} if
     * no build number is available.
     *
     * @return  the build number, or {@link #UNKNOWN_BUILD_NUMBER} is non is available.
     *
     * @see com.zutubi.pulse.core.dependency.ivy.IvyModuleDescriptor#UNKNOWN_BUILD_NUMBER
     */
    public long getBuildNumber()
    {
        Map extraInfo = descriptor.getExtraInfo();
        if (extraInfo.containsKey(EXTRA_INFO_BUILD_NUMBER))
        {
            return Long.valueOf((String)extraInfo.get(EXTRA_INFO_BUILD_NUMBER));
        }
        return UNKNOWN_BUILD_NUMBER;
    }

    /**
     * Set the build number for this ivy descriptor.  This should represent the
     * number of the build that is defined by this descriptor.
     *
     * @param buildNumber  the pulse project build number.
     */
    public void setBuildNumber(long buildNumber)
    {
        @SuppressWarnings({"unchecked"})
        Map<String, String> extraInfo = descriptor.getExtraInfo();
        extraInfo.put(EXTRA_INFO_BUILD_NUMBER, String.valueOf(buildNumber));
    }

    /**
     * Adds the given module to the set of optional dependencies in this
     * descriptor.
     *
     * @param module the (decoded) name of the module to add
     */
    public void addOptionalDependency(String module)
    {
        @SuppressWarnings({"unchecked"})
        Map<String, String> extraInfo = descriptor.getExtraInfo();
        String current = extraInfo.get(EXTRA_INFO_OPTIONAL);
        module = IvyEncoder.encode(module);
        if (current == null)
        {
            current = module;
        }
        else
        {
            current = current + NAME_GLUE + module;
        }

        extraInfo.put(EXTRA_INFO_OPTIONAL, current);
    }

    /**
     * Retrieves the optional dependencies for this descriptor.
     *
     * @return set of all modules that are optional dependencies (decoded)
     */
    public Set<String> getOptionalDependencies()
    {
        String confs = (String) descriptor.getExtraInfo().get(EXTRA_INFO_OPTIONAL);
        if (confs == null)
        {
            return Collections.emptySet();
        }
        else
        {
            String[] decoded = IvyEncoder.decodeNames(StringUtils.split(confs, NAME_GLUE.charAt(0)));
            return new HashSet<String>(asList(decoded));
        }
    }

    /**
     * Get the revision string of the module descriptor.
     *
     * @return the descriptors revision string
     */
    public String getRevision()
    {
        return descriptor.getRevision();
    }

    /**
     * Get the status string of the module descriptor.
     *
     * @return the descriptors status string.
     */
    public String getStatus()
    {
        return descriptor.getStatus();
    }

    /**
     * Get the path (relative to the repository base) of this module descriptor.
     *
     * Note that this path is the encoded path that you would expect to find in the
     * repository.
     *
     * @return  the repository path of this ivy descriptor
     */
    public String getPath()
    {
        return configuration.getIvyPath(descriptor.getModuleRevisionId());
    }

    /**
     * Returns true if the module descriptor has any configured dependencies.
     *
     * @return  true if the module descriptor has dependencies, false otherwise.
     */
    public boolean hasDependencies()
    {
        return descriptor != null && descriptor.getDependencies().length > 0;
    }

    /**
     * Returns true if the module descriptor has any configured artifacts.
     *
     * @return true if the module descriptor has artifacts, false otherwise.
     */
    public boolean hasArtifacts()
    {
        return descriptor != null && descriptor.getAllArtifacts().length > 0;
    }

    /**
     * Returns a list of paths for all of the artifacts defined by this module descriptor.
     * These paths are relative to the repository base.
     *
     * Note that these paths are the encoded paths that you would expect to find in the
     * repository.
     *
     * @return  a list of paths
     */
    public List<String> getArtifactPaths()
    {
        return CollectionUtils.map(descriptor.getAllArtifacts(), new Mapping<Artifact, String>()
        {
            public String map(Artifact artifact)
            {
                return IvyPatternHelper.substitute(configuration.getArtifactPattern(), artifact);
            }
        });
    }

    /**
     * Get the underlying ivy module descriptor.
     *
     * @return underlying ivy module descriptor
     */
    public DefaultModuleDescriptor getDescriptor()
    {
        return descriptor;
    }

    private String ensureConfigurationExists(String confName)
    {
        String encodedConf = IvyEncoder.encode(confName);
        if (descriptor.getConfiguration(encodedConf) == null)
        {
            descriptor.addConfiguration(new Configuration(encodedConf));
        }
        return encodedConf;
    }

    public void setStatus(String status)
    {
        descriptor.setStatus(status);
    }

    /**
     * Get the artifacts in this module descriptor that are associated with the specified stage.
     *
     * @param stageName the stage of interest
     * 
     * @return an array of artifacts.
     */
    public Artifact[] getArtifacts(String stageName)
    {
        return decodeArtifacts(descriptor.getArtifacts(IvyEncoder.encode(stageName)));
    }

    /**
     * Get all of the artifacts in this module descriptor.
     *
     * @return an array of artifacts.
     */
    public Artifact[] getAllArtifacts()
    {
        return decodeArtifacts(descriptor.getAllArtifacts());
    }

    /**
     * Get the module revision id associated with this descriptor.
     *
     * @return the module revision id.
     */
    public ModuleRevisionId getModuleRevisionId()
    {
        return IvyEncoder.decode(descriptor.getModuleRevisionId());
    }

    private Artifact[] decodeArtifacts(Artifact... encodedArtifacts)
    {
        Artifact[] decodedArtifacts = new Artifact[encodedArtifacts.length];
        for (int i = 0; i < encodedArtifacts.length; i++)
        {
            decodedArtifacts[i] = IvyEncoder.decode(encodedArtifacts[i]);
        }
        return decodedArtifacts;
    }

    private String getFilePathBestEffort(File file)
    {
        try
        {
            return file.getCanonicalPath();
        }
        catch (IOException e)
        {
            return file.getAbsolutePath();
        }
    }
}
