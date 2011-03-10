/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.vfs.spi;

import java.io.FileNotFoundException;
import org.jboss.vfs.VirtualFile;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.security.CodeSigner;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/**
 * A real filesystem.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RealFileSystem implements FileSystem {

    private static final Logger log = Logger.getLogger("org.jboss.vfs.real");

    private static final boolean NEEDS_CONVERSION = File.separatorChar != '/';

    private final File realRoot;
    private final boolean extraCaseCheck;

    /**
     * Construct a real filesystem with the given real root.
     *
     * @param realRoot the real root
     */
    public RealFileSystem(File realRoot) {
        this(realRoot, false);
    }

    /**
     * Construct a real filesystem with the given real root.
     *
     * @param realRoot the real root
     * @param extraCaseCheck {@code true} to enable extra case-sensitivity checks
     */
    public RealFileSystem(final File realRoot, final boolean extraCaseCheck) {
        this.realRoot = realRoot;
        this.extraCaseCheck = extraCaseCheck;
        log.tracef("Constructed real filesystem at root %s", realRoot);
    }

    /**
     * {@inheritDoc}
     */
    public InputStream openInputStream(VirtualFile mountPoint, VirtualFile target) throws IOException {
        File file = getFile(mountPoint, target);
        if (file == null) {
            throw new FileNotFoundException(target.getPathName());
        }
        return new FileInputStream(file);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public File getFile(VirtualFile mountPoint, VirtualFile target) {
        final String relativePath = target.getPathNameRelativeTo(mountPoint);
        final File file;
        final char separatorChar = File.separatorChar;
        if (mountPoint.equals(target)) {
            return realRoot;
        } else if (NEEDS_CONVERSION) {
            file = new File(realRoot, relativePath.replace('/', separatorChar));
        } else {
            file = new File(realRoot, relativePath);
        }
        if (extraCaseCheck) try {
            /*
             * Check each segment of the canonical path of the target, relative to the root,
             * to make sure that they are identical to each segment of the virtual target.
             */
            final String canonicalRoot = realRoot.getCanonicalPath();
            final String canonicalTarget = file.getCanonicalPath();
            if (! canonicalTarget.startsWith(canonicalRoot)) {
                // the target does not exist under the root, somehow, so we can't check.
                // in particular, symlinks will screw this up
                return null;
            }
            VirtualFile targetCurrent = target;
            String fileCurrent = canonicalTarget;
            String segment;
            int idx;
            for (;;) {
                // if one or both are equal to the mount point, we're done
                if (fileCurrent.equals(canonicalRoot) || fileCurrent.isEmpty()) {
                    if (targetCurrent.equals(mountPoint)) {
                        return file;
                    } else {
                        return null;
                    }
                } else if (targetCurrent.equals(mountPoint)) {
                    return null;
                }
                idx = fileCurrent.lastIndexOf(separatorChar);
                segment = idx == -1 ? fileCurrent : fileCurrent.substring(idx + 1);
                // check for exact case match
                if (! segment.equals(targetCurrent.getName())) {
                    // if not matched, the file is not found
                    return null;
                }
                // Move each piece to their parent position
                targetCurrent = targetCurrent.getParent();
                fileCurrent = idx == -1 ? "" : fileCurrent.substring(0, idx);
            }
        } catch (IOException e) {
            return null;
        } else {
            return file;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean delete(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? false : file.delete();
    }

    /**
     * {@inheritDoc}
     */
    public long getSize(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? 0L : file.length();
    }

    /**
     * {@inheritDoc}
     */
    public long getLastModified(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? 0L : file.lastModified();
    }

    /**
     * {@inheritDoc}
     */
    public boolean exists(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? false : file.exists();
    }

    /** {@inheritDoc} */
    public boolean isFile(final VirtualFile mountPoint, final VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? false : file.isFile();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDirectory(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        return file == null ? false : file.isDirectory();
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getDirectoryEntries(VirtualFile mountPoint, VirtualFile target) {
        final File file = getFile(mountPoint, target);
        if (file == null) {
            return Collections.emptyList();
        }
        final String[] names = file.list();
        return names == null ? Collections.<String>emptyList() : Arrays.asList(names);
    }
    
    /**
     * {@inheritDoc}
     */
    public CodeSigner[] getCodeSigners(VirtualFile mountPoint, VirtualFile target) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public File getMountSource() {
        return realRoot;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        // no operation - the real FS can't be closed
    }
}
