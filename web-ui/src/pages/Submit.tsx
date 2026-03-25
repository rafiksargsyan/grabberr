import { useState } from 'react';
import {
  Box,
  Typography,
  Tabs,
  Tab,
  TextField,
  Button,
  Alert,
  CircularProgress,
  Paper,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { submitByUrl, submitByFile } from '../api/torrentDownloads';

export function Submit() {
  const { user, accountId } = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);

  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmitUrl = async () => {
    if (!url || !user || !accountId) return;
    setError('');
    setLoading(true);
    try {
      const result = await submitByUrl(url, user, accountId);
      navigate(`/torrent-download/${result.id}`);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Submission failed');
      setLoading(false);
    }
  };

  const handleSubmitFile = async () => {
    if (!file || !user || !accountId) return;
    setError('');
    setLoading(true);
    try {
      const result = await submitByFile(file, user, accountId);
      navigate(`/torrent-download/${result.id}`);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Submission failed');
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight="bold" sx={{ mb: 3 }}>
        Submit Torrent
      </Typography>

      <Paper elevation={1} sx={{ p: 3, maxWidth: 600 }}>
        <Tabs value={tab} onChange={(_, v: number) => setTab(v)} sx={{ mb: 3 }}>
          <Tab label="URL / Magnet" />
          <Tab label="Upload .torrent" />
        </Tabs>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        {tab === 0 ? (
          <Box>
            <TextField
              fullWidth
              label="Magnet link or .torrent URL"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSubmitUrl()}
              disabled={loading}
              placeholder="magnet:?xt=urn:btih:... or https://..."
              sx={{ mb: 2 }}
            />
            <Button
              variant="contained"
              onClick={handleSubmitUrl}
              disabled={loading || !url}
              startIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}
            >
              Submit
            </Button>
          </Box>
        ) : (
          <Box>
            <Button variant="outlined" component="label" disabled={loading} sx={{ mb: 2 }}>
              {file ? file.name : 'Choose .torrent file'}
              <input
                type="file"
                accept=".torrent"
                hidden
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </Button>
            <Box>
              <Button
                variant="contained"
                onClick={handleSubmitFile}
                disabled={loading || !file}
                startIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}
              >
                Submit
              </Button>
            </Box>
          </Box>
        )}
      </Paper>
    </Box>
  );
}
