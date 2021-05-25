import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

if (attachment!=null){
    printWB();
} else{
    println("File attachment unavailable");
}

def printWB(){
    println("Processing attachment " + attachmentName);
    DataFormatter df = new DataFormatter();
    try (XSSFWorkbook wb = new XSSFWorkbook(attachment)) {
        for (int s=0; s < wb.getNumberOfSheets(); s++) {
            XSSFSheet sheet = wb.getSheetAt(s);
            println("Processing sheet " + sheet.getSheetName());
            for (int r=0; r <= sheet.getLastRowNum() && r<=10; r++) {
                XSSFRow row = sheet.getRow(r);
                if (row.getCell(0)==null) {
                    break;
                }
                print("Row " + (r +1) +":\t |");
                for (int c=0; c <= /*row.getLastCellNum()*/ 1; c++) {
                    print(CellReference.convertNumToColString(c) + ": ");
                    print(df.formatCellValue(row.getCell(c, MissingCellPolicy.CREATE_NULL_AS_BLANK)));
                    print("| ");
                }
                println();
            }
        }
    }
}



