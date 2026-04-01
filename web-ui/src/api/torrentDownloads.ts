import type { User } from 'firebase/auth';
import { apiRequest, apiRequestMultipart } from './client';
import type { TorrentDownloadDTO, FileDownloadDTO } from '../types/api.types';

export function listTorrentDownloads(user: User, accountId: string): Promise<TorrentDownloadDTO[]> {
  return apiRequest<TorrentDownloadDTO[]>('/torrent-download', user, { accountId });
}

export function submitByUrl(downloadUrl: string, user: User, accountId: string): Promise<TorrentDownloadDTO> {
  return apiRequest<TorrentDownloadDTO>(
    `/torrent-download?downloadUrl=${encodeURIComponent(downloadUrl)}`,
    user,
    { method: 'POST', accountId },
  );
}

export function submitByFile(file: File, user: User, accountId: string): Promise<TorrentDownloadDTO> {
  const form = new FormData();
  form.append('file', file);
  return apiRequestMultipart<TorrentDownloadDTO>('/torrent-download/upload', user, form, accountId);
}

export function getTorrentDownload(id: string, user: User, accountId: string): Promise<TorrentDownloadDTO> {
  return apiRequest<TorrentDownloadDTO>(`/torrent-download/${id}`, user, { accountId });
}

export function listFileDownloads(id: string, user: User, accountId: string): Promise<FileDownloadDTO[]> {
  return apiRequest<FileDownloadDTO[]>(`/torrent-download/${id}/file`, user, { accountId });
}

export function claimFile(id: string, fileIndex: number, user: User, accountId: string): Promise<FileDownloadDTO> {
  return apiRequest<FileDownloadDTO>(`/torrent-download/${id}/file/${fileIndex}`, user, {
    method: 'PUT',
    accountId,
  });
}

export function getFileDownload(id: string, fileIndex: number, user: User, accountId: string): Promise<FileDownloadDTO> {
  return apiRequest<FileDownloadDTO>(`/torrent-download/${id}/file/${fileIndex}`, user, { accountId });
}

export function deleteTorrentDownload(id: string, user: User, accountId: string): Promise<void> {
  return apiRequest<void>(`/torrent-download/${id}`, user, { method: 'DELETE', accountId });
}

export function cacheFile(id: string, fileIndex: number, user: User, accountId: string): Promise<FileDownloadDTO> {
  return apiRequest<FileDownloadDTO>(`/torrent-download/${id}/file/${fileIndex}/cache`, user, { method: 'POST', accountId });
}

export function extendCacheLifetime(id: string, fileIndex: number, days: number, user: User, accountId: string): Promise<void> {
  return apiRequest<void>(`/torrent-download/${id}/file/${fileIndex}/extend-cache?days=${days}`, user, { method: 'POST', accountId });
}

export function unclaimFile(id: string, fileIndex: number, user: User, accountId: string): Promise<void> {
  return apiRequest<void>(`/torrent-download/${id}/file/${fileIndex}`, user, { method: 'DELETE', accountId });
}
