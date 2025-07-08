package org.example;

import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files; // Import per Files.delete

public class OcrValidatorApp {

    private static final Logger logger = LoggerFactory.getLogger(OcrValidatorApp.class);

    public static void main(String[] args) {
        File imageFile = null; // Dichiarato qui per essere accessibile nel blocco finally

        try {
            if (args.length < 2) {
                logger.error("Utilizzo: java -jar tesseract-ocr-validator-exec.jar <percorso_immagine> <testo_da_cercare>");
                System.exit(1);
            }

            imageFile = new File(args[0]); // Assegna il File qui
            String textToSearch = args[1];

            // Controlli preliminari sul file immagine
            if (!imageFile.exists()) {
                logger.error("L'immagine specificata non esiste: {}", imageFile.getAbsolutePath());
                System.exit(1);
            }
            if (!imageFile.canRead()) {
                logger.error("Non ho i permessi per leggere l'immagine: {}", imageFile.getAbsolutePath());
                System.exit(1);
            }

            String tessDataPath = null;

            // Estrazione delle risorse native e dei dati di Tesseract
            tessDataPath = NativeResourceExtractor.extractAndConfigure();
            logger.info("Dati Tesseract estratti in: {}", tessDataPath);

            // Inizializzazione Tesseract
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage("eng+ita"); // O le lingue appropriate

            logger.info("Avvio OCR sull'immagine: {}", imageFile.getAbsolutePath());
            String result = tesseract.doOCR(imageFile); // Esegue l'OCR
            String trimmedResult = result.trim(); // Rimuove spazi bianchi all'inizio/fine per il confronto

            logger.info("OCR completato. Testo riconosciuto:");
            logger.info("\n{}", result); // Stampa il testo riconosciuto

            // --- Logica di validazione del testo ---
            if (textToSearch.equalsIgnoreCase("vuoto")) {
                // Scenario 1: Ci si aspetta che il testo estratto SIA vuoto
                // SUCCESS se il risultato OCR è vuoto (non c'è testo)
                if (trimmedResult.isEmpty()) {
                    logger.info("SUCCESS: Il testo da cercare era 'vuoto', e il testo estratto è effettivamente vuoto.");
                } else {
                    logger.warn("FAILURE: Il testo da cercare era 'vuoto', ma il testo estratto NON è vuoto. Testo estratto: {}", trimmedResult);
                }
            } else if (textToSearch.equalsIgnoreCase("diversoDaVuoto")) {
                // Scenario 2: Ci si aspetta che il testo estratto NON SIA vuoto
                // SUCCESS se il risultato OCR NON è vuoto (c'è almeno un carattere)
                if (!trimmedResult.isEmpty()) {
                    logger.info("SUCCESS: Il testo da cercare era 'diversoDaVuoto', e il testo estratto NON è vuoto. Testo estratto: {}", trimmedResult);
                } else {
                    logger.warn("FAILURE: Il testo da cercare era 'diversoDaVuoto', ma il testo estratto è vuoto.");
                }
            } else {
                // Scenario 3: Ricerca di un testo specifico (case-insensitive)
                // SUCCESS se il testo estratto contiene il testo cercato (ignorando maiuscole/minuscole)
                if (trimmedResult.toLowerCase().contains(textToSearch.toLowerCase())) {
                    logger.info("SUCCESS: Il testo '{}' è stato trovato nell'immagine (case-insensitive).", textToSearch);
                } else {
                    logger.warn("FAILURE: Il testo '{}' NON è stato trovato nell'immagine (case-insensitive).", textToSearch);
                }
            }

        } catch (IOException e) {
            logger.error("Errore I/O durante l'estrazione delle risorse o l'OCR: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) { // Cattura eccezioni più generiche per debug
            logger.error("FATAL ERROR: Errore inatteso durante l'OCR: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            // --- Pulizia del file PNG di input ---
            if (imageFile != null && imageFile.exists()) {
                try {
                    Files.delete(imageFile.toPath());
                    logger.info("File PNG di input eliminato: {}", imageFile.getAbsolutePath());
                } catch (IOException e) {
                    logger.warn("Impossibile eliminare il file PNG di input {}. Errore: {}", imageFile.getAbsolutePath(), e.getMessage());
                }
            }
            // Pulizia delle risorse native estratte da NativeResourceExtractor
            NativeResourceExtractor.cleanup();
        }
    }
}