package com.zovlanik.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/convertToRUB")
public class ConverterRestControllerV1 {
    private String filepath = "src/main/resources/XML_daily.xml";
    private Map<String, Long> usersAndTimeToDelay = new HashMap<String, Long>();
    private Map<String, Integer> usersAndCounterToDelay = new HashMap<String, Integer>();
    private Long delay = 60_000L; //60 000 милисекунд = 1 минута
    private Integer maxNumOfRequest = 2;


    @GetMapping
    public ResponseEntity<?> convertToRUB(String val, Double sum, @RequestHeader("Authorization") String token) {
        if (usersAndTimeToDelay.containsKey(token)) {
            //если пользователь обращался к нам меньше минуты назад И более 2х раз
            if (usersAndTimeToDelay.get(token) + delay > System.currentTimeMillis() &&
                    usersAndCounterToDelay.get(token) >= maxNumOfRequest) {
                return new ResponseEntity<>("Можно " + maxNumOfRequest + " запроса в " + delay / 1000 + " секунд.", HttpStatus.TOO_MANY_REQUESTS);
            } else if (usersAndTimeToDelay.get(token) + delay > System.currentTimeMillis()) {
                int counter = usersAndCounterToDelay.get(token);
                usersAndCounterToDelay.put(token, ++counter);
            } else {
                usersAndTimeToDelay.put(token, System.currentTimeMillis());
                usersAndCounterToDelay.put(token, 1);
            }
        } else {
            usersAndTimeToDelay.put(token, System.currentTimeMillis());
            usersAndCounterToDelay.put(token, 1);
        }


        //обновим наш файлик, если в нём устаревшие данные
        refreshDailyCurrency();


        try {
            // получаем xml парсер с настройками по умолчанию
            DocumentBuilder xml = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            // разбираем xml и создаем Document
            Document doc = xml.parse(new File(filepath));

            //найдём валюту
            NodeList codeList = doc.getElementsByTagName("CharCode");
            NodeList valueList = doc.getElementsByTagName("Value");
            NodeList nominalList = doc.getElementsByTagName("Nominal");
            for (int i = 0; i < codeList.getLength(); i++) {
                Node codeElement = codeList.item(i);
                //находим, совпадает ли элемент из файлика с нашей переменной
                if (codeElement.getTextContent().equals(val)) {
                    //как правило, в файле все элементы однообразны и находятся на том же уровне,
                    //значит мы можем использовать тот же порядковый номер, как и номер нашей валюты
                    Node valueElement = valueList.item(i);
                    Node nominalElement = nominalList.item(i);
                    Double nominal = Double.parseDouble(nominalElement.getTextContent().replace(',', '.'));
                    Double value = Double.parseDouble(valueElement.getTextContent().replace(',', '.'));

                    return new ResponseEntity<>(sum / nominal * value, HttpStatus.OK);

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(0.0, HttpStatus.NOT_FOUND);
    }

    //метод, обновляющий файлик с валютами
    private void refreshDailyCurrency() {
        Date today = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

        try {
            DocumentBuilder xml = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = xml.parse(new File(filepath));
            NodeList valCurs = doc.getElementsByTagName("ValCurs");
            for (int i = 0; i < valCurs.getLength(); i++) {
                Node element = valCurs.item(i);
                NamedNodeMap namedNodeMap = element.getAttributes();
                //находим в файлике элемент и запоминаем его дату
                Node nodeDate = namedNodeMap.getNamedItem("Date");
                Date dateOfCurrency = simpleDateFormat.parse(nodeDate.getNodeValue());
                //сравниваем с текущей датой и скачиваем заново, если дата меньше
                if (dateOfCurrency.before(today)) {
                    System.out.println("У нас устаревшие данные, скачиваем новые...");
                    //downloadXML();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //метод для скачивания XML файла с ЦБР
    private void downloadXML() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();

            URL url = new URL("http://www.cbr.ru/scripts/XML_daily.asp?");
            InputStream stream = url.openStream();
            Document doc = docBuilder.parse(stream);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Result output = new StreamResult(new File(filepath));
            Source input = new DOMSource(doc);

            transformer.transform(input, output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
