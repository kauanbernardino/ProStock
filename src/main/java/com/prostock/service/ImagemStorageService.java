package com.prostock.service;

import com.prostock.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class ImagemStorageService {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "webp");

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** Salva a imagem em disco e devolve o caminho servido em /uploads/**. */
    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Nenhum arquivo de imagem enviado");
        }

        String nomeOriginal = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "";
        String extensao = extrairExtensao(nomeOriginal);
        if (!EXTENSOES_PERMITIDAS.contains(extensao.toLowerCase())) {
            throw new RegraDeNegocioException(
                    "Formato de imagem não suportado. Use: " + String.join(", ", EXTENSOES_PERMITIDAS));
        }

        try {
            Path pasta = Path.of(uploadDir, "produtos");
            Files.createDirectories(pasta);

            String nomeArquivo = UUID.randomUUID() + "." + extensao;
            Path destino = pasta.resolve(nomeArquivo);

            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/produtos/" + nomeArquivo;
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao salvar a imagem: " + e.getMessage());
        }
    }

    public void remover(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        try {
            String relativo = caminho.replaceFirst("^/uploads/", "");
            Path arquivo = Path.of(uploadDir, relativo);
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorado) {
            // Falha ao apagar o arquivo não impede remover o registro.
        }
    }

    private String extrairExtensao(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        if (ponto < 0 || ponto == nomeArquivo.length() - 1) {
            return "";
        }
        return nomeArquivo.substring(ponto + 1);
    }
}
