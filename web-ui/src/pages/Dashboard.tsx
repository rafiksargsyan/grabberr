import { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  CircularProgress,
  Alert,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { listTorrentDownloads } from '../api/torrentDownloads';
import type { TorrentDownloadDTO, TorrentStatus } from '../types/api.types';

function StatusChip({ status }: { status: TorrentStatus }) {
  const color =
    status === 'READY' ? 'success' :
    status === 'FAILED' ? 'error' : 'warning';
  return <Chip label={status} color={color} size="small" />;
}

export function Dashboard() {
  const { user, accountId } = useAuth();
  const navigate = useNavigate();
  const [downloads, setDownloads] = useState<TorrentDownloadDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user || !accountId) return;
    listTorrentDownloads(user, accountId)
      .then(setDownloads)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, [user, accountId]);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" fontWeight="bold">Downloads</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/submit')}
        >
          Submit torrent
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {loading ? (
        <Box display="flex" justifyContent="center" mt={6}>
          <CircularProgress />
        </Box>
      ) : downloads.length === 0 ? (
        <Typography color="text.secondary">No downloads yet. Submit a torrent to get started.</Typography>
      ) : (
        <TableContainer component={Paper} elevation={1}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Info Hash</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Files</TableCell>
                <TableCell>Submitted</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {downloads.map((d) => (
                <TableRow
                  key={d.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/torrent-download/${d.id}`)}
                >
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{d.id}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                    {d.infoHash.slice(0, 16)}…
                  </TableCell>
                  <TableCell><StatusChip status={d.status} /></TableCell>
                  <TableCell>{d.files.length > 0 ? d.files.length : '—'}</TableCell>
                  <TableCell>{new Date(d.createdAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
