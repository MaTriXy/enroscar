package com.stanfy.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;

import com.stanfy.images.ImagesLoadListener;
import com.stanfy.images.ImagesManager.ImageHolder;
import com.stanfy.images.ImagesManagerContext;

/**
 * Image view that can load a remote image.
 * @author Roman Mazur - Stanfy (http://www.stanfy.com)
 */
public class LoadableImageView extends ImageView implements ImagesLoadListenerProvider {

  /** Skip scaling before caching flag. */
  private boolean skipScaleBeforeCache;
  /** Skip loading indicator flag.  */
  private boolean skipLoadingImage;
  /** Use sampling flag. Sampling can affect the image quality. */
  private boolean useSampling;

  /** Images load listener. */
  private ImagesLoadListener listener;

  /** Images manager context. */
  private ImagesManagerContext<?> imagesManagerContext;

  public LoadableImageView(final Context context) {
    super(context);
  }

  public LoadableImageView(final Context context, final AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public LoadableImageView(final Context context, final AttributeSet attrs, final int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  private void init(final Context context, final AttributeSet attrs) {
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LoadableImageView);
    final boolean skipCache = a.getBoolean(R.styleable.LoadableImageView_skipScaleBeforeCache, false);
    final boolean skipLoadIndicator = a.getBoolean(R.styleable.LoadableImageView_skipLoadingImage, false);
    a.recycle();

    setSkipScaleBeforeCache(skipCache);
    setSkipLoadingImage(skipLoadIndicator);
  }

  /** @param imagesManagerContext the imagesManager context to set */
  public void setImagesManagerContext(final ImagesManagerContext<?> imagesManagerContext) {
    this.imagesManagerContext = imagesManagerContext;
  }

  /** @param skipScaleBeforeCache the skipScaleBeforeCache to set */
  public void setSkipScaleBeforeCache(final boolean skipScaleBeforeCache) { this.skipScaleBeforeCache = skipScaleBeforeCache; }
  /** @return the skipScaleBeforeCache */
  public boolean isSkipScaleBeforeCache() { return skipScaleBeforeCache; }

  /** @param skipLoadingImage the skipLoadingImage to set */
  public void setSkipLoadingImage(final boolean skipLoadingImage) { this.skipLoadingImage = skipLoadingImage; }
  /** @return the skipLoadingImage */
  public boolean isSkipLoadingImage() { return skipLoadingImage; }

  /** @param useSampling the useSampling to set */
  public void setUseSampling(final boolean useSampling) { this.useSampling = useSampling; }
  /** @return the useSampling */
  public boolean isUseSampling() { return useSampling; }

  /** @param listener load listener */
  public void setImagesLoadListener(final ImagesLoadListener listener) {
    this.listener = listener;
    final Object tag = getTag();
    if (tag != null && tag instanceof ImageHolder) { ((ImageHolder) tag).touch(); }
  }
  /** @return images load listener */
  @Override
  public ImagesLoadListener getImagesLoadListener() { return listener; }

  @Override
  public void setImageURI(final Uri uri) {
    if (imagesManagerContext != null && ImagesManagerContext.check(uri)) {
      imagesManagerContext.populateImageView(this, uri != null ? uri.toString() : null);
      return;
    }
    super.setImageURI(uri);
  }

}
