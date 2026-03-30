import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Chip,
  CircularProgress,
  Alert,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Divider,
  Link,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  LinearProgress,
  TextField,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import DeleteIcon from '@mui/icons-material/Delete';
import { useAuth } from '../hooks/useAuth';
import { getTorrentDownload, listFileDownloads, claimFile, getFileDownload, deleteTorrentDownload, cacheFile, extendCacheLifetime } from '../api/torrentDownloads';
import type { TorrentDownloadDTO, FileDownloadDTO, TorrentStatus, FileDownloadStatus } from '../types/api.types';

function TorrentStatusChip({ status }: { status: TorrentStatus }) {
  const color = status === 'READY' ? 'success' : status === 'FAILED' ? 'error' : 'warning';
  return <Chip label={status} color={color} size="small" />;
}

function FileStatusChip({ status }: { status: FileDownloadStatus }) {
  const color = status === 'DONE' ? 'success' : status === 'FAILED' ? 'error' : 'warning';
  return <Chip label={status} color={color} size="small" />;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export function TorrentDownloadDetail() {
  const { id } = useParams<{ id: string }>();
  const { user, accountId } = useAuth();
  const navigate = useNavigate();

  const [torrent, setTorrent] = useState<TorrentDownloadDTO | null>(null);
  const [fileDownloads, setFileDownloads] = useState<Record<number, FileDownloadDTO>>({});
  const [claiming, setClaiming] = useState<Record<number, boolean>>({});
  const [caching, setCaching] = useState<Record<number, boolean>>({});
  const [error, setError] = useState('');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [metadataFile, setMetadataFile] = useState<FileDownloadDTO | null>(null);
  const [extendTarget, setExtendTarget] = useState<FileDownloadDTO | null>(null);
  const [extendDays, setExtendDays] = useState('30');
  const [extending, setExtending] = useState(false);

  const fetchTorrent = useCallback(async () => {
    if (!id || !user || !accountId) return;
    const data = await getTorrentDownload(id, user, accountId);
    setTorrent(data);
  }, [id, user, accountId]);

  const fetchFileDownload = useCallback(async (fileIndex: number) => {
    if (!id || !user || !accountId) return;
    const data = await getFileDownload(id, fileIndex, user, accountId);
    setFileDownloads((prev) => ({ ...prev, [fileIndex]: data }));
  }, [id, user, accountId]);

  // Initial load
  useEffect(() => {
    if (!id || !user || !accountId) return;
    fetchTorrent().catch(console.error);
    listFileDownloads(id, user, accountId)
      .then((list) => {
        const map: Record<number, FileDownloadDTO> = {};
        list.forEach((fd) => { map[fd.fileIndex] = fd; });
        setFileDownloads(map);
      })
      .catch(console.error);
  }, [id, user, accountId, fetchTorrent]);

  useEffect(() => {
    if (!torrent || torrent.status !== 'FETCHING_METADATA') return;
    const interval = setInterval(() => {
      fetchTorrent().catch(console.error);
    }, 5000);
    return () => clearInterval(interval);
  }, [torrent, fetchTorrent]);

  // Poll active file downloads
  useEffect(() => {
    const activeIndexes = Object.entries(fileDownloads)
      .filter(([, fd]) => fd.status === 'DOWNLOADING' || fd.status === 'TRANSFERRING')
      .map(([idx]) => Number(idx));
    if (activeIndexes.length === 0) return;
    const interval = setInterval(() => {
      activeIndexes.forEach((idx) => fetchFileDownload(idx).catch(console.error));
    }, 5000);
    return () => clearInterval(interval);
  }, [fileDownloads, fetchFileDownload]);

  const handleCache = async (fileIndex: number) => {
    if (!id || !user || !accountId) return;
    setCaching((prev) => ({ ...prev, [fileIndex]: true }));
    setError('');
    try {
      const fd = await cacheFile(id, fileIndex, user, accountId);
      setFileDownloads((prev) => ({ ...prev, [fileIndex]: fd }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to cache file');
    } finally {
      setCaching((prev) => ({ ...prev, [fileIndex]: false }));
    }
  };

  const handleExtend = async () => {
    if (!extendTarget || !id || !user || !accountId) return;
    setExtending(true);
    try {
      await extendCacheLifetime(id, extendTarget.fileIndex, parseInt(extendDays), user, accountId);
      const updated = await getFileDownload(id, extendTarget.fileIndex, user, accountId);
      setFileDownloads((prev) => ({ ...prev, [extendTarget.fileIndex]: updated }));
      setExtendTarget(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to extend lifetime');
    } finally {
      setExtending(false);
    }
  };

  const handleDelete = async () => {
    if (!id || !user || !accountId) return;
    setDeleting(true);
    try {
      await deleteTorrentDownload(id, user, accountId);
      navigate('/dashboard');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to delete');
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  const handleClaim = async (fileIndex: number) => {
    if (!id || !user || !accountId) return;
    setClaiming((prev) => ({ ...prev, [fileIndex]: true }));
    setError('');
    try {
      const fd = await claimFile(id, fileIndex, user, accountId);
      setFileDownloads((prev) => ({ ...prev, [fileIndex]: fd }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to claim file');
    } finally {
      setClaiming((prev) => ({ ...prev, [fileIndex]: false }));
    }
  };

  if (!torrent) {
    return (
      <Box display="flex" justifyContent="center" mt={6}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" fontWeight="bold">Torrent Download</Typography>
        <Button color="error" startIcon={<DeleteIcon />} onClick={() => setDeleteOpen(true)}>
          Delete
        </Button>
      </Box>

      <Paper elevation={1} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <TorrentStatusChip status={torrent.status} />
          <Typography variant="body2" color="text.secondary">
            ID: <code>{torrent.id}</code>
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
          {torrent.infoHash}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          Submitted {new Date(torrent.createdAt).toLocaleString()}
        </Typography>

        {torrent.status === 'FETCHING_METADATA' && (
          <Alert severity="info" sx={{ mt: 2 }}>
            Fetching torrent metadata… This page will refresh automatically.
          </Alert>
        )}
        {torrent.status === 'FAILED' && (
          <Alert severity="error" sx={{ mt: 2 }}>
            Metadata fetch timed out. The torrent may be invalid or unavailable.
          </Alert>
        )}
      </Paper>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {torrent.status === 'READY' && torrent.files.length > 0 && (
        <>
          <Divider sx={{ mb: 2 }} />
          <Typography variant="h6" sx={{ mb: 2 }}>Files</Typography>
          <TableContainer component={Paper} elevation={1}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Size</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Expires</TableCell>
                  <TableCell>Metadata</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {torrent.files.map((f) => {
                  const fd = fileDownloads[f.index];
                  return (
                    <TableRow key={f.index}>
                      <TableCell>{f.index}</TableCell>
                      <TableCell sx={{ maxWidth: 300, wordBreak: 'break-all' }}>{f.name}</TableCell>
                      <TableCell>{formatBytes(f.sizeBytes)}</TableCell>
                      <TableCell>
                        {fd ? <FileStatusChip status={fd.status} /> : <Typography variant="body2" color="text.secondary">—</Typography>}
                      </TableCell>
                      <TableCell>
                        {fd?.s3ExpiresAt ? (
                          <Box display="flex" alignItems="center" gap={1}>
                            <Typography variant="body2" color={new Date(fd.s3ExpiresAt) < new Date() ? 'error' : 'text.secondary'}>
                              {new Date(fd.s3ExpiresAt).toLocaleString()}
                            </Typography>
                            <Button size="small" variant="text" onClick={() => { setExtendTarget(fd); setExtendDays('5'); }}>
                              Extend
                            </Button>
                          </Box>
                        ) : (
                          <Typography variant="body2" color="text.secondary">—</Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        {fd?.metadata ? (
                          <Button size="small" variant="text" onClick={() => setMetadataFile(fd)}>View</Button>
                        ) : (
                          <Typography variant="body2" color="text.secondary">—</Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        {fd?.status === 'DONE' && fd.signedUrl ? (
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<DownloadIcon />}
                            component={Link}
                            href={fd.signedUrl}
                            target="_blank"
                            rel="noopener"
                          >
                            Download
                          </Button>
                        ) : fd?.status === 'DONE' && !fd.signedUrl ? (
                          <Button
                            size="small"
                            variant="outlined"
                            color="secondary"
                            onClick={() => handleClaim(f.index)}
                            disabled={claiming[f.index]}
                            startIcon={claiming[f.index] ? <CircularProgress size={14} /> : undefined}
                          >
                            Claim
                          </Button>
                        ) : fd?.status === 'SUBMITTED' ? (
                          <Box display="flex" alignItems="center" gap={1}>
                            <CircularProgress size={16} />
                            <Typography variant="body2">Queued…</Typography>
                          </Box>
                        ) : fd?.status === 'DOWNLOADING' ? (
                          <Box display="flex" alignItems="center" gap={1} sx={{ minWidth: 120 }}>
                            {fd.progress != null ? (
                              <>
                                <LinearProgress variant="determinate" value={fd.progress * 100} sx={{ flex: 1 }} />
                                <Typography variant="body2" noWrap>{Math.round(fd.progress * 100)}%</Typography>
                              </>
                            ) : (
                              <>
                                <CircularProgress size={16} />
                                <Typography variant="body2">Downloading…</Typography>
                              </>
                            )}
                          </Box>
                        ) : fd?.status === 'DOWNLOADED' ? (
                          <Button
                            size="small"
                            variant="outlined"
                            color="warning"
                            onClick={() => handleCache(f.index)}
                            disabled={caching[f.index]}
                            startIcon={caching[f.index] ? <CircularProgress size={14} /> : undefined}
                          >
                            Cache
                          </Button>
                        ) : fd?.status === 'TRANSFERRING' ? (
                          <Box display="flex" alignItems="center" gap={1} sx={{ minWidth: 120 }}>
                            {fd.progress != null ? (
                              <>
                                <LinearProgress variant="determinate" value={fd.progress * 100} sx={{ flex: 1 }} />
                                <Typography variant="body2" noWrap>{Math.round(fd.progress * 100)}%</Typography>
                              </>
                            ) : (
                              <>
                                <CircularProgress size={16} />
                                <Typography variant="body2">Transferring…</Typography>
                              </>
                            )}
                          </Box>
                        ) : fd?.status === 'FAILED' ? (
                          <Button size="small" onClick={() => handleClaim(f.index)} disabled={claiming[f.index]}>
                            Retry
                          </Button>
                        ) : (
                          <Button
                            size="small"
                            variant="outlined"
                            color="secondary"
                            onClick={() => handleClaim(f.index)}
                            disabled={claiming[f.index]}
                            startIcon={claiming[f.index] ? <CircularProgress size={14} /> : undefined}
                          >
                            Claim
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <Dialog open={!!extendTarget} onClose={() => !extending && setExtendTarget(null)}>
        <DialogTitle>Extend cache lifetime</DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          <DialogContentText sx={{ mb: 2 }}>
            Days will be added to the current expiry date.
          </DialogContentText>
          <TextField
            label="Days"
            type="number"
            value={extendDays}
            onChange={(e) => setExtendDays(e.target.value)}
            slotProps={{ htmlInput: { min: 1 } }}
            fullWidth
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setExtendTarget(null)} disabled={extending}>Cancel</Button>
          <Button variant="contained" onClick={handleExtend} disabled={extending || !extendDays || parseInt(extendDays) < 1}>
            {extending ? <CircularProgress size={20} /> : 'Extend'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!metadataFile} onClose={() => setMetadataFile(null)} maxWidth="md" fullWidth>
        <DialogTitle>Metadata</DialogTitle>
        <DialogContent>
          <Box
            component="pre"
            sx={{ fontSize: 12, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all', m: 0 }}
          >
            {metadataFile?.metadata
              ? JSON.stringify(JSON.parse(metadataFile.metadata), null, 2)
              : ''}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMetadataFile(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteOpen} onClose={() => !deleting && setDeleteOpen(false)}>
        <DialogTitle>Delete torrent download?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This will remove your download record. If no other users have claimed this torrent,
            all cached files and S3 objects will be permanently deleted.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)} disabled={deleting}>Cancel</Button>
          <Button color="error" variant="contained" onClick={handleDelete} disabled={deleting}>
            {deleting ? <CircularProgress size={20} /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
