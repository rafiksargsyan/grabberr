export interface UserDTO {
  id: string;
  name: string;
  accountId: string;
}

export interface ApiKeyDTO {
  id: string;
  key: string | null;
  description: string;
  disabled: boolean;
  lastAccessTime: string | null;
}

export type TorrentStatus = 'FETCHING_METADATA' | 'READY' | 'FAILED';
export type FileDownloadStatus = 'SUBMITTED' | 'DOWNLOADING' | 'DOWNLOADED' | 'TRANSFERRING' | 'DONE' | 'FAILED';

export interface TorrentFile {
  index: number;
  name: string;
  sizeBytes: number;
}

export interface TorrentDownloadDTO {
  id: string;
  infoHash: string;
  status: TorrentStatus;
  files: TorrentFile[];
  createdAt: string;
}

export interface FileDownloadDTO {
  id: string;
  fileIndex: number;
  status: FileDownloadStatus;
  progress: number | null;
  signedUrl: string | null;
  fileSizeBytes: number | null;
  completedAt: string | null;
  s3ExpiresAt: string | null;
  createdAt: string;
  metadata: string | null;
}
