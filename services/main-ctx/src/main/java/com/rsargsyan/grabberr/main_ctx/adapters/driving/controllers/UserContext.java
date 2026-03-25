package com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserContext {
  String externalId;
  String userProfileId;
  String accountId;
  String fullName;
}
