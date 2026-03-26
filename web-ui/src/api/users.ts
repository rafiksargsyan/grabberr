import type { User } from 'firebase/auth';
import { apiRequest } from './client';
import type { ApiKeyDTO, UserDTO } from '../types/api.types';

export function signUpExternal(user: User): Promise<UserDTO> {
  return apiRequest<UserDTO>('/user/signup-external', user, { method: 'POST' });
}

export function listApiKeys(user: User, userId: string, accountId: string): Promise<ApiKeyDTO[]> {
  return apiRequest<ApiKeyDTO[]>(`/user/${userId}/api-key`, user, { accountId });
}

export function createApiKey(user: User, userId: string, accountId: string, description: string): Promise<ApiKeyDTO> {
  return apiRequest<ApiKeyDTO>(`/user/${userId}/api-key`, user, {
    method: 'POST',
    accountId,
    body: JSON.stringify({ description }),
  });
}

export function disableApiKey(user: User, userId: string, accountId: string, keyId: string): Promise<ApiKeyDTO> {
  return apiRequest<ApiKeyDTO>(`/user/${userId}/api-key/${keyId}/disable`, user, { method: 'PUT', accountId });
}

export function enableApiKey(user: User, userId: string, accountId: string, keyId: string): Promise<ApiKeyDTO> {
  return apiRequest<ApiKeyDTO>(`/user/${userId}/api-key/${keyId}/enable`, user, { method: 'PUT', accountId });
}

export function deleteApiKey(user: User, userId: string, accountId: string, keyId: string): Promise<void> {
  return apiRequest<void>(`/user/${userId}/api-key/${keyId}`, user, { method: 'DELETE', accountId });
}
