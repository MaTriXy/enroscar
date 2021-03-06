package com.stanfy.net.cache;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import android.util.Log;

import com.stanfy.DebugFlags;
import com.stanfy.app.beans.BeansContainer;
import com.stanfy.app.beans.BeansManager;
import com.stanfy.app.beans.DestroyingBean;
import com.stanfy.app.beans.InitializingBean;
import com.stanfy.app.beans.ManagerAwareBean;
import com.stanfy.io.BuffersPool;
import com.stanfy.io.DiskLruCache;
import com.stanfy.io.DiskLruCache.Snapshot;
import com.stanfy.io.IoUtils;
import com.stanfy.io.PoolableBufferedInputStream;
import com.stanfy.io.PoolableBufferedOutputStream;
import com.stanfy.net.UrlConnectionWrapper;
import com.stanfy.net.cache.CacheEntry.CacheEntryListener;
import com.stanfy.net.cache.CacheEntry.CacheEntryRequest;

/**
 * Base class for cache implementations based on file system.
 * @author Roman Mazur (Stanfy - http://stanfy.com)
 */
public abstract class BaseFileResponseCache extends BaseSizeRestrictedCache
    implements EnhancedResponseCache, ManagerAwareBean, CacheEntryListener, DestroyingBean, InitializingBean {

  /** Cache entry index. */
  private static final int ENTRY_BODY = 0, ENTRY_METADATA = 1;
  /** Count of cache entries. */
  private static final int ENTRIES_COUNT = 2;

  /** Logging tag. */
  protected static final String TAG = "FileCache";
  /** Debug flag. */
  protected static final boolean DEBUG = DebugFlags.DEBUG_BEANS;

  /** Application version. */
  private static final int VERSION = 20120718;

  /** Disk cache instance. */
  private DiskLruCache diskCache;

  /** Buffers pool. */
  private BuffersPool buffersPool;

  /** Statistics. */
  private final AtomicInteger writeSuccessCount = new AtomicInteger(0),
                              writeAbortCount = new AtomicInteger(0),
                              hitCount = new AtomicInteger(0);

  @Override
  public void setBeansManager(final BeansManager beansManager) {
    this.buffersPool = beansManager.getMainBuffersPool();
    if (buffersPool == null) {
      throw new IllegalStateException("Buffers pool must be initialized before the response cache");
    }
  }

  protected void install(final File directory, final int version, final long maxSize, final boolean asyncInit) throws IOException {
    if (buffersPool == null) {
      throw new IllegalStateException("Buffers pool is not resolved");
    }
    directory.mkdirs();
    diskCache = DiskLruCache.open(directory, buffersPool, version, ENTRIES_COUNT, maxSize, asyncInit);
  }

  public void delete() throws IOException {
    if (DEBUG) { Log.d(TAG, "Delete cache workingDirectory=" + diskCache.getDirectory()); }
    diskCache.delete();
  }

  public DiskLruCache getDiskCache() { return diskCache; }

  private DiskLruCache.Snapshot readCacheInfo(final CacheEntry requestInfo, final CacheEntry entry) {
    if (!checkDiskCache()) { return null; }

    final String key = requestInfo.getCacheKey();

    DiskLruCache.Snapshot snapshot;
    PoolableBufferedInputStream bufferedStream = null;
    try {
      snapshot = diskCache.get(key);
      if (snapshot == null) {
        return null;
      }
      bufferedStream = new PoolableBufferedInputStream(snapshot.getInputStream(ENTRY_METADATA), buffersPool);
      entry.readFrom(bufferedStream);
    } catch (final IOException e) {
      IoUtils.closeQuietly(bufferedStream);
      // Give up because the cache cannot be read.
      return null;
    }
    return snapshot;
  }

  protected CacheResponse get(final CacheEntry requestInfo) {
    if (!checkDiskCache()) { return null; }
    final CacheEntry entry = newCacheEntry();
    final DiskLruCache.Snapshot snapshot = readCacheInfo(requestInfo, entry);
    if (snapshot == null) { return null; }

    if (!entry.matches(requestInfo) || !entry.canBeUsed()) {
      snapshot.close();
      return null;
    }

    hitCount.incrementAndGet();

    final InputStream body = newBodyInputStream(snapshot);
    return entry.newCacheResponse(body);
  }

  @Override
  public final CacheResponse get(final URI uri, final URLConnection connection) throws IOException {
    final CacheEntry requestInfo = newCacheEntry();
    requestInfo.setFrom(connection);
    return get(requestInfo);
  }

  @Override
  public final CacheResponse get(final URI uri, final String requestMethod, final Map<String, List<String>> requestHeaders) {
    final CacheEntry requestInfo = newCacheEntry();
    requestInfo.set(uri, requestMethod, requestHeaders);
    return get(requestInfo);
  }

  protected abstract CacheEntry createCacheEntry();

  protected final CacheEntry newCacheEntry() {
    final CacheEntry result = createCacheEntry();
    result.setListener(this);
    return result;
  }

  /**
   * Returns an input stream that reads the body of a snapshot, closing the
   * snapshot when the stream is closed.
   */
  private InputStream newBodyInputStream(final DiskLruCache.Snapshot snapshot) {
    return new FilterInputStream(snapshot.getInputStream(ENTRY_BODY)) {
      @Override public void close() throws IOException {
        snapshot.close();
        super.close();
      }
    };
  }

  @Override
  public CacheRequest put(final URI uri, final URLConnection connection) throws IOException {
    if (!checkDiskCache()) { return null; }
    final URLConnection urlConnection = UrlConnectionWrapper.unwrap(connection);

    final CacheEntry cacheEntry = newCacheEntry();
    cacheEntry.setFrom(urlConnection);
    cacheEntry.setResponseData(urlConnection);

    if (!cacheEntry.canBeCached()) { return null; }

    final String key = cacheEntry.getCacheKey();

    DiskLruCache.Editor editor = null;
    PoolableBufferedOutputStream bufferedOut = null;
    try {
      editor = diskCache.edit(key);
      if (editor == null) {
        return null;
      }
      bufferedOut = new PoolableBufferedOutputStream(editor.newOutputStream(ENTRY_METADATA), this.buffersPool);
      cacheEntry.writeTo(bufferedOut);
      final PoolableBufferedOutputStream output = new PoolableBufferedOutputStream(editor.newOutputStream(ENTRY_BODY), this.buffersPool);
      return cacheEntry.newCacheRequest(output, editor);
    } catch (final IOException e) {
      Log.w(TAG, "Cannot write cache entry", e);
      // Give up because the cache cannot be written.
      try {
        if (editor != null) {
          editor.abort();
        }
      } catch (final IOException ignored) {
        Log.w(TAG, "Cannot abort editor", e);
      }
      return null;
    } finally {
      IoUtils.closeQuietly(bufferedOut);
    }

  }

  private boolean checkDiskCache() {
    if (diskCache == null || diskCache.isClosed()) {
      Log.e(TAG, "File cache is being used but not properly installed, diskCache = " + diskCache);
      return false;
    }
    return true;
  }

  private CacheEntry createGetEntry(final String url) {
    final CacheEntry cacheEntry = newCacheEntry();
    try {
      cacheEntry.set(new URI(url), "GET", Collections.<String, List<String>>emptyMap());
      return cacheEntry;
    } catch (final URISyntaxException e) {
      Log.e(TAG, "Bad url " + url + ", cannot deleteGetEntry", e);
      return null;
    }
  }

  @Override
  public boolean deleteGetEntry(final String url) throws IOException {
    final CacheEntry cacheEntry = createGetEntry(url);
    if (cacheEntry == null) { return false; }
    return diskCache.remove(cacheEntry.getCacheKey());
  }

  @Override
  public boolean contains(final String url) {
    final CacheEntry requestInfo = createGetEntry(url);
    if (requestInfo == null) { return false; }

    final CacheEntry entry = newCacheEntry();
    final Snapshot snapshot = readCacheInfo(requestInfo, entry);
    if (snapshot == null) { return false; }
    IoUtils.closeQuietly(snapshot);

    return entry.matches(requestInfo);
  }

  @Override
  public String getLocalPath(final String url) {
    final CacheEntry requestInfo = createGetEntry(url);
    if (requestInfo == null) { return null; }
    return diskCache.getLocalPath(requestInfo.getCacheKey(), ENTRY_BODY);
  }

  @Override
  public void onCacheEntryWriteAbort(final CacheEntryRequest request) {
    writeAbortCount.incrementAndGet();
  }
  @Override
  public void onCacheEntryWriteSuccess(final CacheEntryRequest request) {
    writeSuccessCount.incrementAndGet();
    try {
      diskCache.flush();
    } catch (final IOException e) {
      Log.w(TAG, "Cannot flush disk cache", e);
    }
  }

  public int getWriteSuccessCount() { return writeSuccessCount.get(); }
  public int getWriteAbortCount() { return writeAbortCount.get(); }
  public int getHitCount() { return hitCount.get(); }

  @Override
  public void onInitializationFinished(final BeansContainer beansContainer) {
    try {
      if (DEBUG) {
        Log.i(TAG, "Install new file cache workingDirectory=" + getWorkingDirectory() + ", version=" + VERSION + ", maxSize=" + getMaxSize());
      }
      install(getWorkingDirectory(), VERSION, getMaxSize(), true);
    } catch (final IOException e) {
      Log.e(TAG, "Cannot install file cache", e);
    }
  }


  @Override
  public void onDestroy(final BeansContainer beansContainer) {
    try {
      if (DEBUG) {
        Log.i(TAG, "Close file cache workingDirectory=" + getWorkingDirectory());
      }
      diskCache.close();
    } catch (final IOException e) {
      Log.e(TAG, "Cannot close file cache", e);
    }
  }

}
