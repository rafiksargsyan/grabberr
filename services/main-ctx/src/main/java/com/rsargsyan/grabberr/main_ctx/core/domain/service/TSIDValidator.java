package com.rsargsyan.grabberr.main_ctx.core.domain.service;

import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidIdException;
import io.hypersistence.tsid.TSID;

public class TSIDValidator {

  public static Long validate(String id) {
    try {
      return TSID.from(id).toLong();
    } catch (IllegalArgumentException e) {
      throw new InvalidIdException();
    }
  }
}
