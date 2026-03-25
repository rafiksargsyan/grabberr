package com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers;

import com.rsargsyan.grabberr.main_ctx.core.app.UserService;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.ApiKeyCreationDTO;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.ApiKeyDTO;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

  private final UserService userService;

  @Autowired
  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/signup-external")
  public ResponseEntity<UserDTO> signupExternal() {
    UserContext ctx = UserContextHolder.get();
    return ResponseEntity.ok(userService.signUpWithExternal(ctx.getExternalId(), ctx.getFullName()));
  }

  @GetMapping("/{userId}/api-key")
  public ResponseEntity<List<ApiKeyDTO>> listApiKeys(@PathVariable String userId) {
    return ResponseEntity.ok(userService.listApiKeys(UserContextHolder.get().getUserProfileId(), userId));
  }

  @PostMapping("/{userId}/api-key")
  public ResponseEntity<ApiKeyDTO> createApiKey(@PathVariable String userId,
                                                @RequestBody ApiKeyCreationDTO req) {
    return new ResponseEntity<>(
        userService.createApiKey(UserContextHolder.get().getUserProfileId(), userId, req.getDescription()),
        HttpStatus.CREATED);
  }

  @PutMapping("/{userId}/api-key/{keyId}/disable")
  public ResponseEntity<ApiKeyDTO> disableApiKey(@PathVariable String userId,
                                                 @PathVariable String keyId) {
    return ResponseEntity.ok(userService.disableApiKey(UserContextHolder.get().getUserProfileId(), userId, keyId));
  }

  @PutMapping("/{userId}/api-key/{keyId}/enable")
  public ResponseEntity<ApiKeyDTO> enableApiKey(@PathVariable String userId,
                                                @PathVariable String keyId) {
    return ResponseEntity.ok(userService.enableApiKey(UserContextHolder.get().getUserProfileId(), userId, keyId));
  }

  @DeleteMapping("/{userId}/api-key/{keyId}")
  public ResponseEntity<Void> deleteApiKey(@PathVariable String userId,
                                           @PathVariable String keyId) {
    userService.deleteApiKey(UserContextHolder.get().getUserProfileId(), userId, keyId);
    return ResponseEntity.noContent().build();
  }
}
