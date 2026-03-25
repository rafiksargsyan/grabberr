package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers.UserContext;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.ApiKey;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.UserProfile;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Principal;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.ApiKeyRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.PrincipalRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.UserProfileRepository;
import io.hypersistence.tsid.TSID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

  private final ApiKeyRepository apiKeyRepository;
  private final UserProfileRepository userProfileRepository;
  private final PrincipalRepository principalRepository;

  @Autowired
  public AuthService(ApiKeyRepository apiKeyRepository,
                     UserProfileRepository userProfileRepository,
                     PrincipalRepository principalRepository) {
    this.apiKeyRepository = apiKeyRepository;
    this.userProfileRepository = userProfileRepository;
    this.principalRepository = principalRepository;
  }

  public boolean validateApiKey(String apiKeyId, String apiKey) {
    if (!TSID.isValid(apiKeyId)) return false;
    return apiKeyRepository.findById(TSID.from(apiKeyId).toLong())
        .map(k -> !k.isDisabled() && k.check(apiKey))
        .orElse(false);
  }

  @Transactional
  public UserContext getUserContextByApiKey(String apiKeyId) {
    if (!TSID.isValid(apiKeyId)) return null;
    ApiKey apiKey = apiKeyRepository.findById(TSID.from(apiKeyId).toLong()).orElse(null);
    if (apiKey == null) return null;
    UserProfile userProfile = apiKey.getUserProfile();
    apiKey.accessed();
    apiKeyRepository.save(apiKey);
    return UserContext.builder()
        .userProfileId(userProfile.getStrId())
        .accountId(userProfile.getAccount().getStrId())
        .externalId(userProfile.getPrincipal().getExternalId())
        .build();
  }

  @Transactional(readOnly = true)
  public String getUserProfileId(String externalId, String accountId) {
    List<Principal> principals = principalRepository.findByExternalId(externalId);
    if (principals.isEmpty()) return null;
    if (!TSID.isValid(accountId)) return null;
    List<UserProfile> userProfiles = userProfileRepository.findByPrincipalIdAndAccountId(
        principals.get(0).getId(), TSID.from(accountId).toLong());
    if (userProfiles.isEmpty()) return null;
    return userProfiles.get(0).getStrId();
  }
}
