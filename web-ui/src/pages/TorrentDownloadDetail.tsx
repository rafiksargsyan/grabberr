import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
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
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { useAuth } from '../hooks/useAuth';
import { getTorrentDownload, claimFile, getFileDownload } from '../api/torrentDownloads';
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

  const [torrent, setTorrent] = useState<TorrentDownloadDTO | null>(null);
  const [fileDownloads, setFileDownloads] = useState<Record<number, FileDownloadDTO>>({});
  const [claiming, setClaiming] = useState<Record<number, boolean>>({});
  const [error, setError] = useState('');

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

  // Initial load + poll torrent status while FETCHING_METADATA
  useEffect(() => {
    fetchTorrent().catch(console.error);
  }, [fetchTorrent]);

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
      .filter(([, fd]) => fd.status === 'DOWNLOADING')
      .map(([idx]) => Number(idx));
    if (activeIndexes.length === 0) return;
    const interval = setInterval(() => {
      activeIndexes.forEach((idx) => fetchFileDownload(idx).catch(console.error));
    }, 5000);
    return () => clearInterval(interval);
  }, [fileDownloads, fetchFileDownload]);

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
      <Typography variant="h5" fontWeight="bold" sx={{ mb: 1 }}>
        Torrent Download
      </Typography>

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
                        ) : fd?.status === 'DOWNLOADING' ? (
                          <Box display="flex" alignItems="center" gap={1}>
                            <CircularProgress size={16} />
                            <Typography variant="body2">Downloading…</Typography>
                          </Box>
                        ) : fd?.status === 'FAILED' ? (
                          <Button size="small" onClick={() => handleClaim(f.index)} disabled={claiming[f.index]}>
                            Retry
                          </Button>
                        ) : (
                          <Button
                            size="small"
                            variant="outlined"
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
    </Box>
  );
}
