package com.mycompany.wccdev;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.stellent.ridc.IdcClient;
import oracle.stellent.ridc.IdcClientException;
import oracle.stellent.ridc.IdcClientManager;
import oracle.stellent.ridc.IdcContext;
import oracle.stellent.ridc.model.DataBinder;
import oracle.stellent.ridc.model.DataObject;
import oracle.stellent.ridc.model.DataResultSet;
import oracle.stellent.ridc.model.TransferFile;
import oracle.stellent.ridc.protocol.ServiceResponse;

public class WCCDev {

    public static void main(String[] args) throws IdcClientException, IOException, SQLException {
        //cargaBD();
        cargaFS();
    }

    public static void cargaBD() throws SQLException, IOException, IdcClientException {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        String dbUser = "WCC01_OCS";
        String dbPassword = "WELCOME1";
        String dbUrl = "db.oracle.com:1521/DB";
        String path = "/temp/";

        IdcClientManager manager = new IdcClientManager();
        IdcClient cliente = manager.createClient("idc://wcc.com:4444");

        IdcContext usuario = new IdcContext("weblogic", "welcome1");

        Connection connection = DriverManager.getConnection("jdbc:oracle:thin:@//" + dbUrl, dbUser, dbPassword);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT * FROM TEMP_DOCUMENTOS");
        while (resultSet.next()) {
            int id = resultSet.getInt("ID");
            String titulo = resultSet.getString("TITULO");
            String seguridad = resultSet.getString("SEGURIDAD");
            String tipo = resultSet.getString("TIPO");
            int version = resultSet.getInt("VERSION");
            String nombreArchivo = resultSet.getString("ARCHIVO");
            int enContent = resultSet.getInt("EN_WCC");
            String dDocName = resultSet.getString("DDOCNAME");

            DataBinder binderOperaciones = cliente.createBinder();
            if (enContent == 1) {
                System.out.println("El documento ya existe en Content");
                binderOperaciones.putLocal("IdcService", "DOC_INFO_BY_NAME");
                binderOperaciones.putLocal("dDocName", dDocName);
                ServiceResponse respuestaServicio = cliente.sendRequest(usuario, binderOperaciones);
                if (respuestaServicio.getResponseType().equals(ServiceResponse.ResponseType.BINDER)) {
                    DataBinder binderRespuesta = respuestaServicio.getResponseAsBinder();
                    System.out.println("BINDER DOC_INFO_BY_NAME: " + binderRespuesta);
                    DataResultSet docInfoDataResultSet = binderRespuesta.getResultSet("DOC_INFO");
                    List<DataObject> docInfoDataObjects = docInfoDataResultSet.getRows();
                    Map<String, Object> metadataList = new HashMap();
                    if (!docInfoDataObjects.isEmpty()) {
                        for (DataObject dataObject : docInfoDataObjects) {
                            for (Map.Entry<String, String> entry : dataObject.entrySet()) {
                                metadataList.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    String dIsCheckedOut = (String) metadataList.get("dIsCheckedOut");
                    System.out.println("IsCheckedOut: " + dIsCheckedOut);
                    if (dIsCheckedOut.equals("0")) {
                        binderOperaciones.putLocal("IdcService", "CHECKOUT_BY_NAME");
                        binderOperaciones.putLocal("dDocName", dDocName);
                        respuestaServicio = cliente.sendRequest(usuario, binderOperaciones);
                        binderRespuesta = respuestaServicio.getResponseAsBinder();
                        System.out.println("BINDER CHECKOUT_BY_NAME: " + binderRespuesta);
                    }
                    //metadataList.remove("dID");
                    //metadataList.remove("dRevisionID");
                    //metadataList.remove("dRevClassID");
                    //metadataList.remove("CurRevID");
                    //metadataList.remove("dRevLabel");
                    binderOperaciones.putLocal("IdcService", "CHECKIN_SEL");

                    System.out.println("BINDER CHECKOUT_BY_NAME: " + binderRespuesta);
                    for (Map.Entry<String, Object> entry : metadataList.entrySet()) {
                        //System.out.println(entry.getKey() + ":" + entry.getValue());
                        binderOperaciones.putLocal(entry.getKey(), (String) entry.getValue());

                    }
                    version += 1;
                    binderOperaciones.putLocal("dRevLabel", String.valueOf(version));
                    TransferFile archivo = new TransferFile(new File(path + nombreArchivo));
                    binderOperaciones.addFile("primaryFile", archivo);
                    respuestaServicio = cliente.sendRequest(usuario, binderOperaciones);
                    binderRespuesta = respuestaServicio.getResponseAsBinder();
                    System.out.println("BINDER CHECKIN: " + binderRespuesta);
                    String dRevLabel = binderRespuesta.getLocal("dRevLabel");
                    Statement stm2 = connection.createStatement();
                    int update = stm2.executeUpdate("UPDATE TEMP_DOCUMENTOS SET VERSION = " + dRevLabel + "  WHERE ID=" + id);
                    System.out.println("Actualizado en la Base de Datos");
                }
            } else {
                binderOperaciones = cliente.createBinder();
                binderOperaciones.putLocal("IdcService", "CHECKIN_NEW");
                binderOperaciones.putLocal("dDocType", tipo);
                binderOperaciones.putLocal("dDocTitle", titulo);
                binderOperaciones.putLocal("dDocAuthor", "weblogic");
                binderOperaciones.putLocal("dSecurityGroup", seguridad);
                binderOperaciones.putLocal("dRevLabel", String.valueOf(version));

                TransferFile archivo = new TransferFile(new File(path + nombreArchivo));
                binderOperaciones.addFile("primaryFile", archivo);

                ServiceResponse respuestaServicio = cliente.sendRequest(usuario, binderOperaciones);

                if (respuestaServicio.getResponseType().equals(ServiceResponse.ResponseType.BINDER)) {
                    DataBinder binderRespuesta = respuestaServicio.getResponseAsBinder();
                    //System.out.println("Binder Respuesta" + binderRespuesta);

                    String dDocNameNuevo = binderRespuesta.getLocal("dDocName");
                    String dRevLabel = binderRespuesta.getLocal("dRevLabel");
                    System.out.println("Status Respuesta Check-In: " + binderRespuesta.getLocal("StatusMessage"));
                    System.out.println("Respuesta Check-In: " + dDocNameNuevo);
                    Statement stm2 = connection.createStatement();
                    int update = stm2.executeUpdate("UPDATE TEMP_DOCUMENTOS SET VERSION = " + dRevLabel + " , EN_WCC = 1, DDOCNAME = '" + dDocNameNuevo + "' WHERE ID=" + id);
                    System.out.println("Actualizado en la Base de Datos");

                } else {
                    System.out.println("Respuesta de otro tipo: " + respuestaServicio.getResponseType());
                }
            }
        }
        connection.close();
    }

    public static void cargaFS() throws SQLException, IOException, IdcClientException {
        String path = "/temp/";

        IdcClientManager manager = new IdcClientManager();
        IdcClient cliente = manager.createClient("idc://wcc.oracle.com:4444");
        IdcContext usuario = new IdcContext("weblogic", "welcome1");

        DataBinder binderOperaciones = cliente.createBinder();

        File pathFile = new File(path);
        String[] files = pathFile.list();
        for (String fileName : files) {
            String dDocTitle = fileName.substring(0, fileName.lastIndexOf("."));
            if (fileName.contains(".pdf") && fileName.contains("DOCUMENTO")) {
                System.out.println("File: " + fileName);

                binderOperaciones.putLocal("IdcService", "CHECKIN_NEW");
                binderOperaciones.putLocal("dDocType", "Titulo");
                binderOperaciones.putLocal("dDocTitle", dDocTitle);
                binderOperaciones.putLocal("dDocAuthor", "weblogic");
                binderOperaciones.putLocal("dSecurityGroup", "Public");
                binderOperaciones.putLocal("dRevLabel", "1");

                TransferFile archivo = new TransferFile(new File(path + fileName));
                binderOperaciones.addFile("primaryFile", archivo);

                ServiceResponse respuestaServicio = cliente.sendRequest(usuario, binderOperaciones);

                if (respuestaServicio.getResponseType().equals(ServiceResponse.ResponseType.BINDER)) {
                    DataBinder binderRespuesta = respuestaServicio.getResponseAsBinder();
                    //System.out.println("Binder Respuesta" + binderRespuesta);

                    String dDocNameNuevo = binderRespuesta.getLocal("dDocName");
                    String dRevLabel = binderRespuesta.getLocal("dRevLabel");
                    System.out.println("Status Respuesta Check-In: " + binderRespuesta.getLocal("StatusMessage"));
                    System.out.println("Respuesta Check-In: " + dDocNameNuevo);

                } else {
                    System.out.println("Respuesta de otro tipo: " + respuestaServicio.getResponseType());
                }
            }
        }
    }
}
