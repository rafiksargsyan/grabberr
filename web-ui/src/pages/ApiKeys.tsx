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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  IconButton,
  Tooltip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useAuth } from '../hooks/useAuth';
import { listApiKeys, createApiKey, disableApiKey, enableApiKey, deleteApiKey } from '../api/users';
import type { ApiKeyDTO } from '../types/api.types';

export function ApiKeys() {
  const { user, userId, accountId } = useAuth();
  const [keys, setKeys] = useState<ApiKeyDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = () => {
    if (!user || !userId || !accountId) return;
    setLoading(true);
    listApiKeys(user, userId, accountId)
      .then(setKeys)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load'))
      .finally(() => setLoading(false));
  };

  useEffect(load, [user, userId, accountId]);

  const handleCreate = async () => {
    if (!user || !userId || !accountId) return;
    setCreating(true);
    try {
      const created = await createApiKey(user, userId, accountId, description);
      setNewKey(created.key);
      setDescription('');
      setCreateOpen(false);
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to create');
    } finally {
      setCreating(false);
    }
  };

  const handleDisable = async (keyId: string) => {
    if (!user || !userId || !accountId) return;
    try {
      await disableApiKey(user, userId, accountId, keyId);
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to disable');
    }
  };

  const handleEnable = async (keyId: string) => {
    if (!user || !userId || !accountId) return;
    try {
      await enableApiKey(user, userId, accountId, keyId);
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to enable');
    }
  };

  const handleDelete = async (keyId: string) => {
    if (!user || !userId || !accountId) return;
    try {
      await deleteApiKey(user, userId, accountId, keyId);
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to delete');
    }
  };

  const handleCopy = (key: string) => {
    navigator.clipboard.writeText(key);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" fontWeight="bold">API Keys</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          New key
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      {newKey && (
        <Alert
          severity="success"
          sx={{ mb: 2, fontFamily: 'monospace' }}
          onClose={() => setNewKey(null)}
          action={
            <Tooltip title={copied ? 'Copied!' : 'Copy'}>
              <IconButton size="small" onClick={() => handleCopy(newKey)}>
                <ContentCopyIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          }
        >
          <Typography variant="body2" fontWeight="bold" mb={0.5}>
            Save this key — it won't be shown again.
          </Typography>
          {newKey}
        </Alert>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" mt={6}>
          <CircularProgress />
        </Box>
      ) : keys.length === 0 ? (
        <Typography color="text.secondary">No API keys yet.</Typography>
      ) : (
        <TableContainer component={Paper} elevation={1}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Last used</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {keys.map((k) => (
                <TableRow key={k.id}>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{k.id}</TableCell>
                  <TableCell>{k.description || <em style={{ color: '#999' }}>No description</em>}</TableCell>
                  <TableCell>
                    <Chip
                      label={k.disabled ? 'Disabled' : 'Active'}
                      color={k.disabled ? 'default' : 'success'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {k.lastAccessTime ? new Date(k.lastAccessTime).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title={k.disabled ? 'Enable' : 'Disable'}>
                      <IconButton size="small" onClick={() => k.disabled ? handleEnable(k.id) : handleDisable(k.id)}>
                        {k.disabled ? <CheckCircleIcon fontSize="small" /> : <BlockIcon fontSize="small" />}
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={k.disabled ? 'Delete' : 'Disable first to delete'}>
                      <span>
                        <IconButton
                          size="small"
                          disabled={!k.disabled}
                          onClick={() => handleDelete(k.id)}
                          color="error"
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>New API Key</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            label="Description"
            fullWidth
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate} disabled={creating || !description.trim()}>
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
