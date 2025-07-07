package org.example;

import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class OcrValidatorApp {

    private static final Logger logger = LoggerFactory.getLogger(OcrValidatorApp.class);

    public static void main(String[] args) {
        // Rimuovi tutto il blocco di codice per l'inizializzazione programmatica qui
        // (ch.qos.logback.classic.LoggerContext, JoranConfigurator, StatusPrinter ecc.)

        logger.info("Inizio applicazione."); // Ora userà il logger

        if (args.length < 2) {
            logger.error("Utilizzo: java -jar tesseract-ocr-validator-exec.jar <percorso_immagine> <testo_da_cercare>");
            System.exit(1);
        }

        File imageFile = new File(args[0]);
        String textToSearch = args[1];

        // Aggiungi questi controlli per l'immagine, usando il logger!
        if (!imageFile.exists()) {
            logger.error("L'immagine specificata non esiste: {}", imageFile.getAbsolutePath());
            System.exit(1);
        }
        if (!imageFile.canRead()) {
            logger.error("Non ho i permessi per leggere l'immagine: {}", imageFile.getAbsolutePath());
            System.exit(1);
        }

        String tessDataPath = null;

        try {
            logger.info("Tentativo di estrazione risorse native e dati Tesseract...");
            tessDataPath = NativeResourceExtractor.extractAndConfigure();
            logger.info("Risorse native e dati Tesseract estratti in: {}", tessDataPath);

            // Il resto del codice dell'OCR
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage("eng+ita");

            logger.info("Avvio OCR sull'immagine: {}", imageFile.getAbsolutePath());
            String result = tesseract.doOCR(imageFile);

            logger.info("OCR completato. Testo riconosciuto:");
            logger.info("\n{}", result); // Stampa il testo riconosciuto su una nuova riga

            if (result.contains(textToSearch)) {
                logger.info("SUCCESS: Il testo '{}' è stato trovato nell'immagine.", textToSearch);
            } else {
                logger.warn("FAILURE: Il testo '{}' NON è stato trovato nell'immagine.", textToSearch);
            }

        } catch (IOException e) {
            logger.error("Errore I/O durante l'estrazione delle risorse o l'OCR: {}", e.getMessage(), e);
            // Non chiamare e.printStackTrace() qui, il logger lo farà se configurato con un layout appropriato
            System.exit(1);
        } catch (Exception e) { // Cattura Exception generica per errori inattesi
            logger.error("FATAL ERROR: Errore inatteso durante l'OCR: {}", e.getMessage(), e);
            // Non chiamare e.printStackTrace() qui
            System.exit(1);
        } finally {
            NativeResourceExtractor.cleanup();
        }
    }
}