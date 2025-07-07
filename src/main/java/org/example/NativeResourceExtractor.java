package org.example;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI; // Import corretto per toURI()
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NativeResourceExtractor {

    private static final Logger logger = LoggerFactory.getLogger(NativeResourceExtractor.class);
    private static File tempExtractionDir;

    private NativeResourceExtractor() {
        // Utility class
    }

    /**
     * Estrae le librerie native e i file tessdata dal JAR in una directory temporanea
     * e imposta jna.library.path per le librerie native.
     *
     * @return Il percorso della directory temporanea dove sono stati estratti i file tessdata.
     * @throws IOException Se si verifica un errore durante l'estrazione.
     */
    public static String extractAndConfigure() throws IOException {
        long startTime = System.currentTimeMillis();
        logger.info("Avvio estrazione e configurazione delle risorse native e tessdata...");

        // Pulisci una potenziale vecchia directory se l'app è stata riavviata senza chiudere correttamente
        cleanup();

        // Crea una directory temporanea unica per questa esecuzione
        tempExtractionDir = Files.createTempDirectory("ocr_validator_resources_").toFile();
        tempExtractionDir.deleteOnExit(); // Assicurati che venga pulita all'uscita della JVM

        String osArch = getOsArchitecture();
        String nativeLibPathInJar = "native/" + osArch + "/"; // Percorso interno al JAR
        String tessdataPathInJar = "tessdata/"; // Percorso interno al JAR

        File jarFile = null;
        try {
            // Ottiene il percorso del JAR in esecuzione
            URI jarUri = NativeResourceExtractor.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            // Gestisce URL con spazi o caratteri speciali
            if ("jar".equals(jarUri.getScheme())) {
                String path = jarUri.getSchemeSpecificPart();
                int bangIdx = path.indexOf('!');
                if (bangIdx != -1) {
                    path = path.substring(0, bangIdx);
                }
                jarFile = new File(new URI(path));
            } else {
                jarFile = new File(jarUri);
            }
        } catch (Exception e) {
            logger.error("Impossibile ottenere il percorso del JAR: {}", e.getMessage(), e);
            throw new IOException("Impossibile trovare il JAR in esecuzione per estrarre le risorse.", e);
        }

        if (jarFile == null || !jarFile.exists() || !jarFile.getName().endsWith(".jar")) {
            logger.warn("Non eseguo da un JAR valido. Assumo che le risorse siano accessibili dal classpath (ambiente IDE).");
            // Se non è un JAR, imposta il jna.library.path alla directory del progetto per debug in IDE
            // Questo potrebbe richiedere configurazioni specifiche nell'IDE per funzionare
            System.setProperty("jna.library.path", new File("src/main/resources/native/" + osArch).getAbsolutePath());
            logger.info("jna.library.path per IDE impostato a: {}", System.getProperty("jna.library.path"));
            return new File("src/main/resources/tessdata").getAbsolutePath(); // Percorso dei dati per IDE
        }

        logger.info("JAR in esecuzione: {}", jarFile.getAbsolutePath());

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // *** MODIFICA QUI: Estrai i file nativi direttamente nella root della tempExtractionDir ***
                if (name.startsWith(nativeLibPathInJar) && !entry.isDirectory()) {
                    // Prende solo il nome del file (es. libtesseract.5.dylib)
                    String fileName = new File(name).getName();
                    File outFile = new File(tempExtractionDir, fileName); // Metti il file direttamente in tempExtractionDir
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Estratto libreria nativa a: {}", outFile.getAbsolutePath());
                    }
                }
                // Estrai i file tessdata nella sottocartella tessdata della tempExtractionDir
                else if (name.startsWith(tessdataPathInJar) && !entry.isDirectory()) {
                    // Mantieni la struttura della sottocartella tessdata
                    String relativePath = entry.getName().substring(tessdataPathInJar.length());
                    File outFile = new File(tempExtractionDir, tessdataPathInJar + relativePath);
                    outFile.getParentFile().mkdirs(); // Crea le directory necessarie
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Estratto file tessdata a: {}", outFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Errore durante l'estrazione delle risorse dal JAR: {}", e.getMessage(), e);
            cleanup(); // Tentativo di pulizia in caso di errore
            throw new IOException("Impossibile estrarre le risorse necessarie: " + e.getMessage(), e);
        }

        // Imposta la proprietà jna.library.path alla directory temporanea dove sono state estratte le librerie native
        System.setProperty("jna.library.path", tempExtractionDir.getAbsolutePath());
        logger.info("jna.library.path impostato a: {}", System.getProperty("jna.library.path"));

        long endTime = System.currentTimeMillis();
        logger.info("Estrazione e configurazione completate in {} ms.", (endTime - startTime));
        return tempExtractionDir.getAbsolutePath() + File.separator + "tessdata"; // Ritorna il percorso completo per i dati di Tesseract
    }

    // Il metodo extractResource non è più necessario separatamente con la logica sopra

    private static String getOsArchitecture() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        if (os.contains("win")) {
            return "win32-" + arch; // Es: win32-x86-64
        } else if (os.contains("mac")) {
            if (arch.contains("arm") || arch.contains("aarch64")) {
                return "macos-arm64"; // Per Mac M1/M2/M3
            }
            return "macos-x86_64"; // Per Mac Intel
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return "linux-" + arch;
        }
        throw new UnsupportedOperationException("Sistema operativo/architettura non supportati per l'estrazione nativa: " + os + "-" + arch);
    }

    /**
     * Esegue la pulizia della directory temporanea di estrazione.
     * Chiamare nel blocco finally dell'applicazione principale.
     */
    public static void cleanup() {
        if (tempExtractionDir != null && tempExtractionDir.exists()) {
            try {
                FileUtils.deleteDirectory(tempExtractionDir);
                logger.info("Directory temporanea delle risorse eliminata: {}", tempExtractionDir.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Errore durante l'eliminazione della directory temporanea {}: {}", tempExtractionDir.getAbsolutePath(), e.getMessage());
            } finally {
                tempExtractionDir = null; // Resetta il riferimento dopo la pulizia
            }
        }
    }
}