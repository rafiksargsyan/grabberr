export interface UserDTO {
  id: string;
  name: string;
  accountId: string;
}

export type TorrentStatus = 'FETCHING_METADATA' | 'READY' | 'FAILED';
export type FileDownloadStatus = 'DOWNLOADING' | 'DONE' | 'FAILED';

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
  signedUrl: string | null;
  fileSizeBytes: number | null;
  completedAt: string | null;
  createdAt: string;
}
