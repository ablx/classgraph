/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.classgraph;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;

import nonapi.io.github.classgraph.utils.FileUtils;
import nonapi.io.github.classgraph.utils.InputStreamOrByteBufferAdapter;

/**
 * A classpath or module path resource (i.e. file) that was found in a whitelisted/non-blacklisted package inside a
 * classpath element or module.
 */
public abstract class Resource implements Closeable, Comparable<Resource> {

    /** The input stream, or null. */
    protected InputStream inputStream;

    /** The byte buffer, or null. */
    protected ByteBuffer byteBuffer;

    /** The length, or -1L for unknown. */
    protected long length = -1L;

    /** True if the resource is open. */
    protected boolean isOpen;

    /** The cached result of toString(). */
    private String toString;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Create an {@link InputStream} from a {@link ByteBuffer}.
     *
     * @return the input stream
     */
    protected InputStream byteBufferToInputStream() {
        return inputStream == null ? inputStream = FileUtils.byteBufferToInputStream(byteBuffer) : inputStream;
    }

    /**
     * Create a {@link ByteBuffer} from an {@link InputStream}.
     *
     * @return the byte buffer
     * @throws IOException
     *             if an I/O exception occurs.
     */
    protected ByteBuffer inputStreamToByteBuffer() throws IOException {
        return byteBuffer == null ? byteBuffer = ByteBuffer.wrap(inputStreamToByteArray()) : byteBuffer;
    }

    /**
     * Read all bytes from an {@link InputStream} and return as a byte array.
     *
     * @return the contents of the {@link InputStream}.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    protected byte[] inputStreamToByteArray() throws IOException {
        return FileUtils.readAllBytesAsArray(inputStream, length);
    }

    /**
     * Read/copy contents of a {@link ByteBuffer} as a byte array.
     *
     * @return the contents of the {@link ByteBuffer} as a byte array.
     */
    protected byte[] byteBufferToByteArray() {
        if (byteBuffer.hasArray()) {
            return byteBuffer.array();
        } else {
            final byte[] byteArray = new byte[byteBuffer.remaining()];
            byteBuffer.get(byteArray);
            return byteArray;
        }
    }

    /**
     * Class for closing the parent {@link Resource} when an {@link InputStream} opened on the resource is closed.
     */
    protected class InputStreamResourceCloser extends InputStream {

        /** The input stream. */
        private InputStream inputStream;

        /** The parent resource. */
        private Resource parentResource;

        /**
         * Constructor.
         *
         * @param parentResource
         *            the parent resource
         * @param inputStream
         *            the input stream
         * @throws IOException
         *             if an I/O exception occurs.
         */
        protected InputStreamResourceCloser(final Resource parentResource, final InputStream inputStream)
                throws IOException {
            super();
            if (inputStream == null) {
                throw new IOException("InputStream cannot be null");
            }
            this.inputStream = inputStream;
            this.parentResource = parentResource;
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#read()
         */
        @Override
        public int read() throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            return inputStream.read();
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#read(byte[], int, int)
         */
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            return inputStream.read(b, off, len);
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#read(byte[])
         */
        @Override
        public int read(final byte[] b) throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            return inputStream.read(b);
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#available()
         */
        @Override
        public int available() throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            return inputStream.available();
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#skip(long)
         */
        @Override
        public long skip(final long n) throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            return inputStream.skip(n);
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#markSupported()
         */
        @Override
        public boolean markSupported() {
            return inputStream.markSupported();
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#mark(int)
         */
        @Override
        public synchronized void mark(final int readlimit) {
            inputStream.mark(readlimit);
        }

        /* (non-Javadoc)
         * @see java.io.InputStream#reset()
         */
        @Override
        public synchronized void reset() throws IOException {
            if (inputStream == null) {
                throw new IOException("InputStream is not open");
            }
            inputStream.reset();
        }

        /**
         * Close the wrapped InputStream, but don't close parent resource.
         *
         * @throws IOException
         *             if an I/O exception occurs.
         */
        void closeInputStream() throws IOException {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    // Ignore
                }
                inputStream = null;
            }
        }

        /**
         * Close the parent resource by calling {@link Resource#close()}, which will call
         * {@link #closeInputStream()}.
         *
         * @throws IOException
         *             if an I/O exception occurs.
         */
        @Override
        public void close() throws IOException {
            if (parentResource != null) {
                parentResource.close();
                parentResource = null;
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Mark the resource as open.
     * 
     * @throws IOException
     *             If the resource is already open.
     */
    protected void markAsOpen() throws IOException {
        if (isOpen) {
            throw new IOException("Resource is already open -- cannot open it again without first calling close()");
        }
        isOpen = true;
    }

    /** Mark the resource as closed. */
    protected void markAsClosed() {
        isOpen = false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the path of this classpath resource relative to the package root.
     *
     * @return the path of this classpath resource relative to the package root. For example, for a resource path of
     *         {@code "BOOT-INF/classes/com/xyz/resource.xml"} and a package root of {@code "BOOT-INF/classes/"},
     *         returns {@code "com/xyz/resource.xml"}. Also drops version prefixes for multi-version jars, for
     *         example for a resource path of {@code "META-INF/versions/11/com/xyz/resource.xml"} while running on
     *         JDK 9+, returns {@code "com/xyz/resource.xml"}.
     */
    public abstract String getPath();

    /**
     * Get the full path of this classpath resource relative to the root of the classpath element.
     *
     * @return the full path of this classpath resource within the classpath element. For example, will return the
     *         full path of {@code "BOOT-INF/classes/com/xyz/resource.xml"} or
     *         {@code "META-INF/versions/11/com/xyz/resource.xml"}, not {@code "com/xyz/resource.xml"}.
     */
    public abstract String getPathRelativeToClasspathElement();

    /**
     * Get the {@link URL} representing the resource's location.
     *
     * @return A {@link URL} representing the resource's location. May point to a temporary file that ClassGraph
     *         extracted an inner jar or directory to, or downloaded a remote jar to. You may or may not be able to
     *         fetch content from the URL.
     * @throws IllegalArgumentException
     *             if a {@link MalformedURLException} occurred while trying to construct the URL.
     */
    public abstract URL getURL();

    /**
     * Get the classpath element {@link URL}.
     *
     * @return The {@link URL} of the classpath element that this resource was found within.
     */
    public abstract URL getClasspathElementURL();

    /**
     * Get the classpath element {@link File}.
     *
     * @return The {@link File} for the classpath element package root dir or jar that this {@Resource} was found
     *         within, or null if this {@link Resource} was found in a module. (See also {@link #getModuleRef}.)
     */
    public abstract File getClasspathElementFile();

    /**
     * Get the The {@link ModuleRef} for the module that this {@link Resource} was found within.
     *
     * @return The {@link ModuleRef} for the module that this {@link Resource} was found within, as a
     *         {@link ModuleRef}, or null if this {@link Resource} was found in a directory or jar in the classpath.
     *         (See also {@link #getClasspathElementFile()}.)
     */
    public abstract ModuleRef getModuleRef();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Open an {@link InputStream} for a classpath resource. Make sure you call {@link Resource#close()} when you
     * are finished with the {@link InputStream}, so that the {@link InputStream} is closed.
     * 
     * @return The opened {@link InputStream}.
     * @throws IOException
     *             If the {@link InputStream} could not be opened.
     */
    public abstract InputStream open() throws IOException;

    /**
     * Open a {@link ByteBuffer} for a classpath resource. Make sure you call {@link Resource#close()} when you are
     * finished with the {@link ByteBuffer}, so that the {@link ByteBuffer} is released or unmapped.
     * 
     * @return The allocated or mapped {@link ByteBuffer} for the resource file content.
     * @throws IOException
     *             If the resource could not be opened.
     */
    public abstract ByteBuffer read() throws IOException;

    /**
     * Load a classpath resource and return its content as a byte array. Automatically calls
     * {@link Resource#close()} after loading the byte array and before returning it, so that the underlying
     * InputStream is closed or the underlying ByteBuffer is released or unmapped.
     * 
     * @return The contents of the resource file.
     * @throws IOException
     *             If the file contents could not be loaded in their entirety.
     */
    public abstract byte[] load() throws IOException;

    /**
     * Open a {@link ByteBuffer}, if there is an efficient underlying mechanism for opening one, otherwise open an
     * {@link InputStream}.
     *
     * @return the {@link InputStreamOrByteBufferAdapter}
     * @throws IOException
     *             if an I/O exception occurs.
     */
    abstract InputStreamOrByteBufferAdapter openOrRead() throws IOException;

    /**
     * Get the length of the resource.
     *
     * @return The length of the resource. This only reliably returns a valid value after calling {@link #open()},
     *         {@link #read()}, or {@link #load()} (and for {@link #open()}, only if the underlying jarfile has
     *         length information for corresponding {@link ZipEntry} -- some jarfiles may not have length
     *         information in their zip entries). Returns -1L if the length is unknown.
     */
    public long getLength() {
        return length;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get a string representation of the resource's location (as a URL string).
     *
     * @return the resource location as a URL String.
     */
    @Override
    public String toString() {
        if (toString != null) {
            return toString;
        } else {
            return toString = getURL().toString();
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Resource)) {
            return false;
        }
        return this.toString().equals(obj.toString());
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final Resource o) {
        return toString().compareTo(o.toString());
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Close the underlying InputStream, or release/unmap the underlying ByteBuffer. */
    @Override
    public void close() {
        // Override in subclasses, and call super.close()
        if (inputStream != null) {
            try {
                if (inputStream instanceof InputStreamResourceCloser) {
                    ((InputStreamResourceCloser) inputStream).closeInputStream();
                } else {
                    inputStream.close();
                }
            } catch (final IOException e) {
                // Ignore
            }
            inputStream = null;
        }
    }
}
