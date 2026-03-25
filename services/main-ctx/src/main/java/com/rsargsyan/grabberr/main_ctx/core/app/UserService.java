package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.ApiKeyCreationDTO;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.ApiKeyDTO;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.UserDTO;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.*;
import com.rsargsyan.grabberr.main_ctx.core.exception.ApiKeyNotDisabledException;
import com.rsargsyan.grabberr.main_ctx.core.exception.AuthorizationException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

  private final UserProfileRepository userProfileRepository;
  private final AccountRepository accountRepository;
  private final PrincipalRepository principalRepository;
  private final ApiKeyRepository apiKeyRepository;

  @Autowired
  public UserService(UserProfileRepository userProfileRepository,
                     AccountRepository accountRepository,
                     PrincipalRepository principalRepository,
                     ApiKeyRepository apiKeyRepository) {
    this.userProfileRepository = userProfileRepository;
    this.accountRepository = accountRepository;
    this.principalRepository = principalRepository;
    this.apiKeyRepository = apiKeyRepository;
  }

  @Transactional
  public UserDTO signUpWithExternal(String externalId, String name) {
    List<Principal> principalList = principalRepository.findByExternalId(externalId);
    if (!principalList.isEmpty()) {
      var existing = userProfileRepository.findByPrincipalId(principalList.get(0).getId());
      if (existing.isEmpty()) throw new ResourceNotFoundException();
      return UserDTO.from(existing.get(0));
    }
    if (name == null || name.isBlank()) name = "Your full name here";
    Principal principal = new Principal(externalId, name);
    Account account = new Account();
    UserProfile userProfile = new UserProfile(account, principal, name);
    principalRepository.save(principal);
    accountRepository.save(account);
    userProfileRepository.save(userProfile);
    return UserDTO.from(userProfile);
  }

  public List<ApiKeyDTO> listApiKeys(String actingUserId, String userProfileIdStr) {
    Long userProfileId = Util.validateTSID(userProfileIdStr);
    if (!userProfileIdStr.equals(actingUserId)) throw new AuthorizationException();
    return apiKeyRepository.findByUserProfileId(userProfileId).stream()
        .map(ApiKeyDTO::from).toList();
  }

  @Transactional
  public ApiKeyDTO createApiKey(String actingUserId, String userProfileIdStr, String description) {
    Long userProfileId = Util.validateTSID(userProfileIdStr);
    if (!userProfileIdStr.equals(actingUserId)) throw new AuthorizationException();
    UserProfile userProfile = userProfileRepository.findById(userProfileId)
        .orElseThrow(ResourceNotFoundException::new);
    String key = userProfile.createApiKey(description);
    userProfileRepository.save(userProfile);
    ApiKey apiKey = userProfile.getApiKeyByKey(key);
    return ApiKeyDTO.from(apiKey, key);
  }

  @Transactional
  public ApiKeyDTO disableApiKey(String actingUserId, String userProfileIdStr, String apiKeyIdStr) {
    Long userProfileId = Util.validateTSID(userProfileIdStr);
    Long apiKeyId = Util.validateTSID(apiKeyIdStr);
    if (!userProfileIdStr.equals(actingUserId)) throw new AuthorizationException();
    ApiKey apiKey = apiKeyRepository.findByIdAndUserProfileId(apiKeyId, userProfileId)
        .orElseThrow(ResourceNotFoundException::new);
    apiKey.disable();
    apiKeyRepository.save(apiKey);
    return ApiKeyDTO.from(apiKey);
  }

  @Transactional
  public ApiKeyDTO enableApiKey(String actingUserId, String userProfileIdStr, String apiKeyIdStr) {
    Long userProfileId = Util.validateTSID(userProfileIdStr);
    Long apiKeyId = Util.validateTSID(apiKeyIdStr);
    if (!userProfileIdStr.equals(actingUserId)) throw new AuthorizationException();
    ApiKey apiKey = apiKeyRepository.findByIdAndUserProfileId(apiKeyId, userProfileId)
        .orElseThrow(ResourceNotFoundException::new);
    apiKey.enable();
    apiKeyRepository.save(apiKey);
    return ApiKeyDTO.from(apiKey);
  }

  @Transactional
  public void deleteApiKey(String actingUserId, String userProfileIdStr, String apiKeyIdStr) {
    Long userProfileId = Util.validateTSID(userProfileIdStr);
    Long apiKeyId = Util.validateTSID(apiKeyIdStr);
    if (!userProfileIdStr.equals(actingUserId)) throw new AuthorizationException();
    ApiKey apiKey = apiKeyRepository.findByIdAndUserProfileId(apiKeyId, userProfileId)
        .orElseThrow(ResourceNotFoundException::new);
    if (!apiKey.isDisabled()) throw new ApiKeyNotDisabledException();
    apiKeyRepository.deleteById(apiKeyId);
  }
}
