import aquality.selenium.core.utilities.ISettingsFile;
import aquality.selenium.core.utilities.JsonSettingsFile;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import utils.enums.DocNames;
import utils.enums.SettingsValues;
import utils.TXTUtils;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CreateDocument {
    private static final ISettingsFile settingsFile = new JsonSettingsFile("settings.json");

    public static void main(String[] args) {

        //Конвертирование .docx в .txt
        try {
            File file = new File(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                    DocNames.INPUT_WORD_DOC_NAME.getValue());
            FileInputStream fis = new FileInputStream(file.getAbsolutePath());
            XWPFDocument doc = new XWPFDocument(fis);
            XWPFWordExtractor ex = new XWPFWordExtractor(doc);
            String text = ex.getText();

            File fil = new File(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                    DocNames.CONVERTED_TXT_FILE_NAME.getValue());
            Writer output = new BufferedWriter(new FileWriter(fil));
            output.write(text);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        TXTUtils.readFile(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                        DocNames.CONVERTED_TXT_FILE_NAME.getValue(),
                settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                        DocNames.FORMATTED_TXT_FILE_NAME.getValue());

        //Копирование шаблонного файла
        try {
            Files.copy(Path.of(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                            DocNames.TEMPLATE_WORD_DOC_NAME.getValue()),
                    Path.of(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                            DocNames.COPIED_TEMPLATE_WORD_DOC_NAME.getValue()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            //Открытие скопированного файла
            XWPFDocument document = new XWPFDocument(OPCPackage.open(
                    settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                            DocNames.COPIED_TEMPLATE_WORD_DOC_NAME.getValue()));

            //Получение списка текста из .txt файла
            List<String> text = getTextFromFileTxt(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                    DocNames.FORMATTED_TXT_FILE_NAME.getValue());

            //Получение номера строки для вставки текста
            int counter = 0;
            List<IBodyElement> elements = document.getBodyElements();
            for (; counter < elements.size(); counter++) {
                IBodyElement element = elements.get(counter);
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph p1 = (XWPFParagraph) element;
                    List<XWPFRun> runList = p1.getRuns();
                    StringBuilder sb = new StringBuilder();
                    for (XWPFRun run : runList)
                        sb.append(run.getText(0));
                    if (sb.toString().contains("12345")) {
                        document.removeBodyElement(counter);
                        counter--;
                        break;
                    }
                }
            }

            //Вставка текста
            addNumList(document, text, counter, 0, "%1.", 1);

            //Создание выходного файла
            FileOutputStream out = new FileOutputStream(new File(
                    settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                            DocNames.OUTPUT_WORD_DOC_NAME.getValue()));

            document.write(out);
            document.close();

            //Удаление скопированного файла
            deleteFile(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                    DocNames.COPIED_TEMPLATE_WORD_DOC_NAME.getValue());
            deleteFile(settingsFile.getValue(SettingsValues.FOLDER.getValue()) +
                    DocNames.CONVERTED_TXT_FILE_NAME.getValue());
        } catch (IOException |
                InvalidFormatException e) {
            e.printStackTrace();
        }
    }

    public static void deleteFile(String fileName) {
        new File(fileName).delete();
    }

    public static void addNumList(XWPFDocument document, List<String> text, int counter, int absNum, String lvlText, int startNum) {
        CTAbstractNum cTAbstractNum = CTAbstractNum.Factory.newInstance();
        cTAbstractNum.setAbstractNumId(BigInteger.valueOf(absNum));
        CTLvl cTLvl = cTAbstractNum.addNewLvl();
        cTLvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        cTLvl.addNewLvlText().setVal(lvlText);
        cTLvl.addNewStart().setVal(BigInteger.valueOf(startNum));

        XWPFAbstractNum abstractNum = new XWPFAbstractNum(cTAbstractNum);
        XWPFNumbering numbering = document.createNumbering();
        BigInteger abstractNumID = numbering.addAbstractNum(abstractNum);
        BigInteger numID = numbering.addNum(abstractNumID);

        for (String line : text) {
            XWPFParagraph paragraph = document.getParagraphs().get(counter);
            XmlCursor cursor = paragraph.getCTP().newCursor();
            XWPFParagraph new_par = document.insertNewParagraph(cursor);
            XWPFRun run = new_par.createRun();

            new_par.setAlignment(ParagraphAlignment.BOTH);
            new_par.setSpacingAfter(0);
            new_par.setSpacingBetween(1);
            new_par.setIndentationFirstLine(851);

            if (!line.isEmpty()) {
                line = line.substring(getFirstLetterIndex(line) - 1);

                new_par.setNumID(numID);
                run.setText(line);
                run.setFontSize(14);
                run.setFontFamily("Times New Roman");
            } else {
                new_par.createRun().setText(line);
            }
            counter++;
        }
    }

    private static List<String> getTextFromFileTxt(String fileName) throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(fileName));
        List<String> text = new ArrayList<>();

        while (scanner.hasNextLine()) {
            text.add(scanner.nextLine());
        }

        return text;
    }

    private static Integer getFirstLetterIndex(String textLine) {
        int counter = 0;
        for (char character : textLine.toCharArray()) {
            counter++;
            if (character >= 'А' && character <= 'я') {
                break;
            }
        }
        return counter;
    }
}