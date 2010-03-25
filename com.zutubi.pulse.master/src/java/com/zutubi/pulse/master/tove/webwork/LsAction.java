package com.zutubi.pulse.master.tove.webwork;

import com.zutubi.pulse.master.vfs.CompoundFileFilter;
import com.zutubi.pulse.master.vfs.FilePrefixFilter;
import com.zutubi.pulse.master.vfs.provider.pulse.AbstractPulseFileObject;
import com.zutubi.pulse.master.vfs.provider.pulse.ComparatorProvider;
import com.zutubi.pulse.master.xwork.actions.vfs.DirectoryComparator;
import com.zutubi.pulse.master.xwork.actions.vfs.FileObjectWrapper;
import com.zutubi.pulse.master.xwork.actions.vfs.VFSActionSupport;
import com.zutubi.tove.type.record.PathUtils;
import com.zutubi.util.*;
import static com.zutubi.util.CollectionUtils.asPair;
import org.apache.commons.vfs.*;
import org.apache.commons.vfs.provider.UriParser;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * The ls action provides access to 'ls' style functionality for the web ui.
 */
public class LsAction extends VFSActionSupport
{
    private String fs ="pulse";
    private String prefix;

    /**
     * The base path for the request.  This, combined with the {@link #path}
     * define the path to be listed.
     */
    private String basePath;

    /**
     * The path, relative to the base path, that defines what should be listed.
     */
    private String path;

    /**
     * The results of the ls action.
     */
    private ExtFile[] listing;

    /**
     * Show files indicates whether or not the listing should include files. The default value is false.
     */
    private boolean showFiles = true;

    /**
     * Show files that are marked as hidden. The default value for this is false.
     */
    private boolean showHidden;

    /**
     * Number of levels, under the one being listed, to also load.  The default
     * of zero means just list this path's direct children.
     */
    private int depth = 0;

    /**
     * If not null, the name of a property to filter files by.  This is the
     * name of a boolean bean property on the file object.  Files without such
     * a property are always passed by the filter.
     */
    private String filterFlag = null;

    public String getBasePath()
    {
        return basePath;
    }

    public void setBasePath(String basePath)
    {
        this.basePath = basePath;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public void setFs(String fs)
    {
        this.fs = fs;
    }

    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    public void setShowFiles(boolean showFiles)
    {
        this.showFiles = showFiles;
    }

    public void setShowHidden(boolean showHidden)
    {
        this.showHidden = showHidden;
    }

    public void setFilterFlag(String filterFlag)
    {
        this.filterFlag = filterFlag;
    }

    public void setDepth(int depth)
    {
        this.depth = depth;
    }

    public ExtFile[] getListing()
    {
        return listing;
    }

    public String execute() throws Exception
    {
        String fullPath = fs + "://";
        if(StringUtils.stringSet(basePath))
        {
            fullPath += "/" + UriParser.encode(PathUtils.normalisePath(basePath));
        }
        if(StringUtils.stringSet(path))
        {
            fullPath += "/" + UriParser.encode(PathUtils.normalisePath(path));
        }

        final FileObject fileObject = getFS().resolveFile(fullPath);

        // can only list a file object if
        // a) it is a directory
        if (fileObject.getType() != FileType.FOLDER)
        {
            return ERROR;
        }

        // b) the user has read permissions.
        if (!fileObject.isReadable())
        {
            addActionError("You do not have permission to list this folder.");
            return ERROR;
        }

        Collection<FileType> acceptedTypes = new HashSet<FileType>();
        acceptedTypes.add(FileType.FOLDER);
        if (showFiles)
        {
            acceptedTypes.add(FileType.FILE);
        }

        FileFilterSelector selector = new FileFilterSelector(
                new CompoundFileFilter(
                        new FileTypeFilter(acceptedTypes),
                        new HiddenFileFilter(showHidden),
                        new FilePrefixFilter(prefix),
                        new FlagFileFilter(filterFlag)
                )
        );

        listing = listChildren(fileObject, selector, 0);

        return SUCCESS;
    }

    private ExtFile[] listChildren(final FileObject fileObject, final FileFilterSelector selector, final int currentDepth) throws FileSystemException
    {
        ExtFile[] extFiles = null;
        FileObject[] children = fileObject.findFiles(selector);
        if (children != null)
        {
            sortChildren(fileObject, children);
            extFiles = new ExtFile[children.length];
            CollectionUtils.mapToArray(children, new Mapping<FileObject, ExtFile>()
            {
                public ExtFile map(FileObject child)
                {
                    ExtFile extFile = new ExtFile(new FileObjectWrapper(child, fileObject));
                    if (!extFile.isLeaf() && currentDepth < depth)
                    {
                        try
                        {
                            extFile.addChildren(listChildren(child, selector, currentDepth + 1));
                        }
                        catch (FileSystemException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }

                    return extFile;
                }
            }, extFiles);
        }

        return extFiles;
    }

    private void sortChildren(FileObject fileObject, FileObject[] children)
    {
        Comparator<FileObject> comparator = getComparator(fileObject);
        if (comparator != null)
        {
            Arrays.sort(children, comparator);
        }
    }

    private Comparator<FileObject> getComparator(FileObject parentFile)
    {
        if(parentFile instanceof AbstractPulseFileObject)
        {
            try
            {
                ComparatorProvider provider = ((AbstractPulseFileObject) parentFile).getAncestor(ComparatorProvider.class);
                if (provider != null)
                {
                    return provider.getComparator();
                }
            }
            catch (FileSystemException e)
            {
                // Fall through to default.
            }
        }

        return new DirectoryComparator();
    }

    /**
     * Filter that accepts only specified types of files.
     */
    private static class FileTypeFilter implements FileFilter
    {
        private Collection<FileType> acceptedTypes = new HashSet<FileType>();

        private FileTypeFilter(Collection<FileType> acceptedTypes)
        {
            this.acceptedTypes = acceptedTypes;
        }

        public boolean accept(final FileSelectInfo fileInfo)
        {
            try
            {
                return acceptedTypes.contains(fileInfo.getFile().getType());
            }
            catch (FileSystemException e)
            {
                return false;
            }
        }
    }

    /**
     * Filter based on the files hidden flag.
     */
    private static class HiddenFileFilter implements FileFilter
    {
        private boolean showHidden;

        private HiddenFileFilter(boolean showHidden)
        {
            this.showHidden = showHidden;
        }

        public boolean accept(FileSelectInfo fileSelectInfo)
        {
            try
            {
                FileObject file = fileSelectInfo.getFile();
                return showHidden || !file.isHidden();
            }
            catch (FileSystemException e)
            {
                return false;
            }
        }
    }

    /**
     * Filter based on arbitrary flags represented by bean properties.  The
     * property must be true for the filter to pass.
     */
    private static class FlagFileFilter implements FileFilter
    {
        /**
         * Keeps a cache of (class, flag name) pairs mapped to (boolean,
         * method) pairs.  The map entry boolean indicates if a read method
         * exists.
         */
        private static final Map<Pair<Class, String>, Pair<Boolean, Method>> cache = new HashMap<Pair<Class, String>, Pair<Boolean, Method>>();

        private String flag;

        private FlagFileFilter(String flag)
        {
            this.flag = flag;
        }

        public boolean accept(FileSelectInfo fileSelectInfo)
        {
            if (!StringUtils.stringSet(flag))
            {
                return true;
            }

            FileObject file = fileSelectInfo.getFile();
            Class fileClass = file.getClass();
            Pair<Boolean, Method> entry = lookup(fileClass);
            if (entry.first)
            {
                try
                {
                    return (Boolean) entry.second.invoke(file);
                }
                catch (Exception e)
                {
                    return false;
                }
            }
            else
            {
                // Flag doesn't exist in the file class, don't filter this type
                // of file.
                return true;
            }
        }

        private Pair<Boolean, Method> lookup(Class fileClass)
        {
            Pair<Boolean, Method> entry;
            synchronized (cache)
            {
                Pair<Class, String> key = asPair(fileClass, flag);
                entry = cache.get(key);
                if (entry == null)
                {
                    entry = introspect(fileClass);
                    cache.put(key, entry);
                }
            }
            return entry;
        }

        private Pair<Boolean, Method> introspect(Class fileClass)
        {
            try
            {
                BeanInfo beanInfo = Introspector.getBeanInfo(fileClass);
                PropertyDescriptor descriptor = CollectionUtils.find(beanInfo.getPropertyDescriptors(), new Predicate<PropertyDescriptor>()
                {
                    public boolean satisfied(PropertyDescriptor propertyDescriptor)
                    {
                        if (propertyDescriptor.getName().equals(flag))
                        {
                            Method readMethod = propertyDescriptor.getReadMethod();
                            return readMethod != null && readMethod.getReturnType() == Boolean.TYPE;
                        }

                        return false;
                    }
                });

                if (descriptor != null)
                {
                    return asPair(true, descriptor.getReadMethod());
                }
            }
            catch (IntrospectionException e)
            {
                // Fall through.
            }

            return asPair(false, null);
        }
    }
}
