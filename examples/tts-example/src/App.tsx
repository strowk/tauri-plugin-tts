import { useState, useEffect, useRef } from "react";
import {
  Box,
  Button,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Slider,
  Stack,
  Alert,
  CircularProgress,
  Typography,
  Paper,
  Container,
} from "@mui/material";
import { MdPlayArrow, MdStop, MdRefresh } from "react-icons/md";
import {
  speak,
  stop,
  getVoices,
  isSpeaking as guestIsSpeaking,
  type Voice,
} from "tauri-plugin-tts-api";

export default function App() {
  const [text, setText] = useState(
    "Hello! This is a test of the text-to-speech plugin."
  );
  const [selectedVoiceId, setSelectedVoiceId] = useState("");
  const [rate, setRate] = useState(1.0);
  const [pitch, setPitch] = useState(1.0);
  const [volume, setVolume] = useState(1.0);
  const [voices, setVoices] = useState<Voice[]>([]);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Load available voices on mount
  useEffect(() => {
    loadVoices();
    return () => {
      // Cleanup polling on unmount
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
      }
    };
  }, []);

  // Start polling for speaking status (only when speaking starts)
  const startPolling = () => {
    if (pollingRef.current) return; // Already polling

    setIsSpeaking(true); // Optimistic update

    pollingRef.current = setInterval(async () => {
      try {
        const speaking = await guestIsSpeaking();
        setIsSpeaking(speaking);

        // Stop polling when speech ends
        if (!speaking && pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      } catch {
        // Ignore errors in polling
      }
    }, 500);
  };

  // Stop polling manually
  const stopPolling = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    setIsSpeaking(false);
  };

  const loadVoices = async () => {
    setLoading(true);
    setError(null);
    try {
      const availableVoices = await getVoices();
      setVoices(availableVoices);
      setSuccess(`Loaded ${availableVoices.length} voices`);
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(`Failed to load voices: ${err}`);
    } finally {
      setLoading(false);
    }
  };

  const handleSpeak = async () => {
    setError(null);
    try {
      await speak({
        text,
        voiceId: selectedVoiceId || undefined,
        rate,
        pitch,
        volume,
      });
      startPolling(); // Start polling only when speaking starts
      setSuccess("Speaking started!");
      setTimeout(() => setSuccess(null), 2000);
    } catch (err) {
      setError(`Failed to speak: ${err}`);
    }
  };

  const handleStop = async () => {
    setError(null);
    try {
      await stop();
      stopPolling(); // Stop polling immediately
      setSuccess("Speech stopped");
      setTimeout(() => setSuccess(null), 2000);
    } catch (err) {
      setError(`Failed to stop: ${err}`);
    }
  };

  // Group voices by language
  const voicesByLanguage = voices.reduce(
    (acc, voice) => {
      const lang = voice.language.split("-")[0];
      if (!acc[lang]) acc[lang] = [];
      acc[lang].push(voice);
      return acc;
    },
    {} as Record<string, Voice[]>
  );

  return (
    <Container maxWidth="md" sx={{ py: { xs: 2, sm: 3, md: 4 } }}>
      <Paper
        elevation={0}
        sx={{
          p: { xs: 2, sm: 3 },
          mb: { xs: 2, sm: 3 },
          borderRadius: 2,
          background: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
          color: "white",
        }}
      >
        <Typography
          variant="h4"
          component="h1"
          sx={{
            fontSize: { xs: "1.5rem", sm: "2rem", md: "2.125rem" },
            fontWeight: 700,
            mb: 1,
          }}
        >
          🔊 Text-to-Speech Example
        </Typography>
        <Typography
          variant="body2"
          sx={{
            fontSize: { xs: "0.875rem", sm: "1rem" },
            opacity: 0.9,
            display: { xs: "none", sm: "block" },
          }}
        >
          Test the native Text-to-Speech plugin functionality
        </Typography>
      </Paper>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          onClose={() => setSuccess(null)}
        >
          {success}
        </Alert>
      )}

      <Stack spacing={{ xs: 2, sm: 3 }}>
        {/* Text input */}
        <TextField
          label="Text to speak"
          multiline
          rows={3}
          value={text}
          onChange={e => setText(e.target.value)}
          fullWidth
        />

        {/* Voice selector */}
        <FormControl fullWidth>
          <InputLabel>Voice</InputLabel>
          <Select
            value={selectedVoiceId}
            label="Voice"
            onChange={e => setSelectedVoiceId(e.target.value)}
          >
            <MenuItem value="">
              <em>System Default</em>
            </MenuItem>
            {Object.entries(voicesByLanguage).map(([lang, langVoices]) => [
              <MenuItem
                key={`header-${lang}`}
                disabled
                sx={{ fontWeight: "bold", bgcolor: "action.hover" }}
              >
                {lang.toUpperCase()} ({langVoices.length} voices)
              </MenuItem>,
              ...langVoices.map(voice => (
                <MenuItem key={voice.id} value={voice.id} sx={{ pl: 4 }}>
                  {voice.name}
                </MenuItem>
              )),
            ])}
          </Select>
        </FormControl>

        {/* Rate slider */}
        <Box>
          <Typography
            gutterBottom
            sx={{ fontSize: { xs: "0.875rem", sm: "1rem" } }}
          >
            Rate: {rate.toFixed(2)}x
          </Typography>
          <Slider
            value={rate}
            onChange={(_, value) => setRate(value as number)}
            min={0.25}
            max={2.0}
            step={0.05}
            marks={[
              { value: 0.25, label: "0.25x" },
              { value: 0.5, label: "0.5x" },
              { value: 1.0, label: "1x" },
              { value: 2.0, label: "2x" },
            ]}
            sx={{
              "& .MuiSlider-markLabel": {
                fontSize: { xs: "0.625rem", sm: "0.75rem" },
              },
            }}
          />
        </Box>

        {/* Pitch slider */}
        <Box>
          <Typography
            gutterBottom
            sx={{ fontSize: { xs: "0.875rem", sm: "1rem" } }}
          >
            Pitch: {pitch.toFixed(1)}
          </Typography>
          <Slider
            value={pitch}
            onChange={(_, value) => setPitch(value as number)}
            min={0.5}
            max={2.0}
            step={0.1}
            marks={[
              { value: 0.5, label: "Low" },
              { value: 1.0, label: "Normal" },
              { value: 2.0, label: "High" },
            ]}
            sx={{
              "& .MuiSlider-markLabel": {
                fontSize: { xs: "0.625rem", sm: "0.75rem" },
              },
            }}
          />
        </Box>

        {/* Volume slider */}
        <Box>
          <Typography
            gutterBottom
            sx={{ fontSize: { xs: "0.875rem", sm: "1rem" } }}
          >
            Volume: {Math.round(volume * 100)}%
          </Typography>
          <Slider
            value={volume}
            onChange={(_, value) => setVolume(value as number)}
            min={0}
            max={1.0}
            step={0.1}
            marks={[
              { value: 0, label: "Mute" },
              { value: 0.5, label: "50%" },
              { value: 1.0, label: "100%" },
            ]}
            sx={{
              "& .MuiSlider-markLabel": {
                fontSize: { xs: "0.625rem", sm: "0.75rem" },
              },
            }}
          />
        </Box>

        {/* Action buttons */}
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <Button
            variant="contained"
            color="primary"
            startIcon={<MdPlayArrow />}
            onClick={handleSpeak}
            disabled={!text.trim() || isSpeaking}
            fullWidth
            sx={{ minHeight: { xs: 48, sm: 44 } }}
          >
            Speak
          </Button>
          <Button
            variant="outlined"
            color="secondary"
            startIcon={<MdStop />}
            onClick={handleStop}
            disabled={!isSpeaking}
            fullWidth
            sx={{ minHeight: { xs: 48, sm: 44 } }}
          >
            Stop
          </Button>
        </Stack>

        {/* Status */}
        <Paper
          sx={{
            p: { xs: 1.5, sm: 2 },
            bgcolor: isSpeaking ? "success.light" : "grey.100",
          }}
        >
          <Typography
            variant="body2"
            textAlign="center"
            sx={{ fontSize: { xs: "0.875rem", sm: "1rem" } }}
          >
            Status: {isSpeaking ? "🔊 Speaking..." : "🔇 Idle"}
          </Typography>
        </Paper>

        {/* Voices list */}
        <Paper sx={{ p: { xs: 1.5, sm: 2 } }}>
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="center"
            sx={{ mb: 2 }}
          >
            <Typography variant="h6">
              Available Voices ({voices.length})
            </Typography>
            <Button
              size="small"
              startIcon={
                loading ? <CircularProgress size={16} /> : <MdRefresh />
              }
              onClick={loadVoices}
              disabled={loading}
            >
              Refresh
            </Button>
          </Stack>

          {voices.length === 0 && !loading && (
            <Typography color="text.secondary" textAlign="center">
              No voices found. Click refresh to load.
            </Typography>
          )}

          <Box sx={{ maxHeight: 300, overflow: "auto" }}>
            {Object.entries(voicesByLanguage).map(([lang, langVoices]) => (
              <Box key={lang} sx={{ mb: 2 }}>
                <Typography variant="subtitle2" color="primary">
                  {lang.toUpperCase()} ({langVoices.length})
                </Typography>
                {langVoices.slice(0, 5).map(voice => (
                  <Button
                    key={voice.id}
                    variant="text"
                    size="small"
                    onClick={() => setSelectedVoiceId(voice.id)}
                    sx={{
                      pl: 2,
                      justifyContent: "flex-start",
                      textTransform: "none",
                      fontWeight:
                        selectedVoiceId === voice.id ? "bold" : "normal",
                      color:
                        selectedVoiceId === voice.id
                          ? "primary.main"
                          : "text.primary",
                    }}
                  >
                    • {voice.name} ({voice.language})
                  </Button>
                ))}
                {langVoices.length > 5 && (
                  <Typography
                    variant="caption"
                    sx={{ pl: 2 }}
                    color="text.secondary"
                  >
                    ... and {langVoices.length - 5} more
                  </Typography>
                )}
              </Box>
            ))}
          </Box>
        </Paper>

        {/* Sample phrases */}
        <Paper sx={{ p: { xs: 1.5, sm: 2 }, mt: { xs: 2, sm: 3 } }}>
          <Typography
            variant="h6"
            gutterBottom
            sx={{ fontSize: { xs: "1rem", sm: "1.25rem" } }}
          >
            Sample Phrases (click to set text)
          </Typography>
          <Stack spacing={1}>
            {[
              { lang: "en", text: "Hello! How are you today?" },
              { lang: "pt", text: "Olá! Como você está hoje?" },
              { lang: "es", text: "¡Hola! ¿Cómo estás hoy?" },
              { lang: "fr", text: "Bonjour! Comment allez-vous?" },
              { lang: "de", text: "Hallo! Wie geht es Ihnen?" },
              { lang: "ja", text: "こんにちは！お元気ですか？" },
            ].map(sample => {
              // Find first voice for this language
              const langVoices = voicesByLanguage[sample.lang] || [];
              const firstVoice = langVoices[0];
              return (
                <Button
                  key={sample.lang}
                  variant="text"
                  size="small"
                  onClick={() => {
                    setText(sample.text);
                    if (firstVoice) {
                      setSelectedVoiceId(firstVoice.id);
                    }
                  }}
                  sx={{ justifyContent: "flex-start", textTransform: "none" }}
                >
                  [{sample.lang.toUpperCase()}] {sample.text}
                </Button>
              );
            })}
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}
