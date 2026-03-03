package com.meetingnotes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;

@Slf4j
@Service
public class TranscriptionService {

    @Value("${openai.api.key}")
    private String openAiKey;

    private static final String WHISPER_URL =
            "https://api.openai.com/v1/audio/transcriptions";

    /**
     * Sends audio/video file to OpenAI Whisper and returns transcript text.
     * Whisper supports: mp3, mp4, mpeg, mpga, m4a, wav, webm
     */
    public String transcribe(MultipartFile file) throws Exception {
        // Detect file extension from original filename
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".webm";

        // Save to temp file — Whisper needs actual bytes
        Path tmpFile = Files.createTempFile("recording_", ext);
        file.transferTo(tmpFile.toFile());
        log.info("Transcribing file: {} ({} bytes)", originalName, file.getSize());

        try {
            String boundary = "----MeetingMindBoundary" + System.currentTimeMillis();
            byte[] fileBytes = Files.readAllBytes(tmpFile);

            ByteArrayOutputStream body = new ByteArrayOutputStream();

            // Helper to write text parts
            writeField(body, boundary, "model", "whisper-1");
            writeField(body, boundary, "response_format", "text");

            // File part header
            String fileHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"recording" + ext + "\"\r\n" +
                    "Content-Type: " + file.getContentType() + "\r\n\r\n";
            body.write(fileHeader.getBytes());
            body.write(fileBytes);
            body.write("\r\n".getBytes());

            // Closing boundary
            body.write(("--" + boundary + "--\r\n").getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHISPER_URL))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(java.time.Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Whisper status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Whisper API error: " + response.body());
            }

            String transcript = response.body().trim();
            log.info("Transcript length: {} chars", transcript.length());
            return transcript;

        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    private void writeField(ByteArrayOutputStream out, String boundary,
                            String name, String value) throws IOException {
        String part = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                value + "\r\n";
        out.write(part.getBytes());
    }
}