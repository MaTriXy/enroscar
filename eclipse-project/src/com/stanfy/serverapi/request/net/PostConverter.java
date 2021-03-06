package com.stanfy.serverapi.request.net;

import java.io.IOException;
import java.net.URLConnection;

import android.content.Context;

import com.stanfy.serverapi.request.RequestDescription;

/**
 * @author Roman Mazur (Stanfy - http://stanfy.com)
 */
public abstract class PostConverter extends BaseRequestDescriptionConverter {

  /** Default content type. */
  private final String contentType;

  public PostConverter(final RequestDescription requestDescription, final Context context, final String contentType) {
    super(requestDescription, context);
    this.contentType = contentType;
  }

  @Override
  public URLConnection prepareConnectionInstance() throws IOException {
    final URLConnection connection = requestDescription.prepareConnectionBuilder(context)
        .setUrl(requestDescription.getUrl())
        .create();
    connection.setDoOutput(true);

    final String rdContentType = requestDescription.getContentType();
    if (rdContentType == null) {
      connection.addRequestProperty("Content-Type", this.contentType);
    }

    return connection;
  }

}
