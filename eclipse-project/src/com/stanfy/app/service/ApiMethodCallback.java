package com.stanfy.app.service;

import com.stanfy.serverapi.request.RequestDescription;
import com.stanfy.serverapi.response.ResponseData;

/**
 * Callback for {@link ApiMethods}.
 * @author Roman Mazur (Stanfy - http://stanfy.com)
 */
public interface ApiMethodCallback {

  /**
   * Report API operation success.
   * @param requestDescription request description
   * @param responseData response data instance
   */
  void reportSuccess(final RequestDescription requestDescription, final ResponseData<?> responseData);

  /**
   * Report API operation error.
   * @param requestDescription request description
   * @param responseData response data instance
   */
  void reportError(final RequestDescription requestDescription, final ResponseData<?> responseData);

  /**
   * Report API operation cancel.
   * @param requestDescription request description
   * @param responseData response data instance
   */
  void reportCancel(final RequestDescription requestDescription, final ResponseData<?> responseData);

}
