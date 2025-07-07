package org.example;

import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Lascia questo import, Logback dovrebbe gestirlo

import java.io.File;
import java.io.IOException;

public class OcrValidatorApp {

    private static final Logger logger = LoggerFactory.getLogger(OcrValidatorApp.class);

    public static void main(String[] args) {
        System.out.println("DEBUG: Inizio applicazione."); // Questa stampa!
        logger.info("DEBUG: Inizio applicazione."); // Questa stampa dovrebbe essere visibile nel log

        if (args.length < 2) {
            System.out.println("ERROR: Utilizzo: java -jar tesseract-ocr-validator-exec.jar <percorso_immagine> <testo_da_cercare>"); // Cambio a System.out
            System.exit(1);
        }

        File imageFile = new File(args[0]);
        String textToSearch = args[1];

        String tessDataPath = null;

        try {
            System.out.println("DEBUG: Tentativo di estrazione risorse native e dati Tesseract...");
            tessDataPath = NativeResourceExtractor.extractAndConfigure();
            System.out.println("DEBUG: Risorse native e dati Tesseract estratti in: " + tessDataPath); // Questa dovrebbe stampare!

            // Il resto del codice dell'OCR
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage("eng+ita");

            System.out.println("DEBUG: Avvio OCR sull'immagine: " + imageFile.getAbsolutePath());
            String result = tesseract.doOCR(imageFile);

            System.out.println("DEBUG: OCR completato. Testo riconosciuto:");
            System.out.println("\n" + result);

            if (result.contains(textToSearch)) {
                System.out.println("Final: SUCCESS: Il testo '" + textToSearch + "' è stato trovato nell'immagine.");
            } else {
                System.out.println("Final: FAILURE: Il testo '" + textToSearch + "' NON è stato trovato nell'immagine.");
            }

        } catch (IOException e) {
            System.err.println("ERROR: Errore I/O durante l'estrazione delle risorse o l'OCR: " + e.getMessage());
            e.printStackTrace(); // Stampa lo stack trace
            System.exit(1);
        } catch (Exception e) {
            System.err.println("FATAL ERROR: Errore inatteso durante l'OCR: " + e.getMessage());
            e.printStackTrace(); // Stampa lo stack trace
            System.exit(1);
        } finally {
            NativeResourceExtractor.cleanup();
        }
    }
}