package umltonosqlplugin.actions;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ViewManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ActionController implements VPActionController {

    private ArrayList<Path> paths;
    private HashMap<IAssociation, IClass> assoClasses;
    private ArrayList<String> geneClasses;
    private ArrayList<String> classesToDelete;
    private String pathToDir;
    private ViewManager viewManager;
    private IDiagramUIModel diagram;


    public ActionController() {
    }

    @Override
    public void performAction(VPAction vpAction) {
        umlToNoSQL();
    }

    @Override
    public void update(VPAction vpAction) {

    }

    private void umlToNoSQL() {
        initializeUmlToNoSQL();
        showMessage("Operation started");
        showDirDialog();
        deleteAllFilesInDir(new File(pathToDir));
        IProject project = ApplicationManager.instance().getProjectManager().getProject();
        performProjectTransformation(project);
        showMessage("Operation completed");
    }

    private void initializeUmlToNoSQL() {
        setViewManager();
        paths = new ArrayList<>();
        assoClasses = new HashMap<>();
        geneClasses = new ArrayList<>();
        classesToDelete = new ArrayList<>();
    }

    private void setViewManager() {
        viewManager = ApplicationManager.instance().getViewManager();
    }

    private void performProjectTransformation(IProject project) {
        getAssoGeneClasses(project);
        Iterator diagramIter = project.diagramIterator();
        while (diagramIter.hasNext()) {
            diagram = (IDiagramUIModel) diagramIter.next();
            if (diagram.getType().equals("ClassDiagram")) {
                showMessage("--------------------------------------------------------------------------------------------------------------------------------------");
                showMessage("Processing started: " + diagram.getName() + " : " + diagram.getType());
                showMessage("--------------------------------------------------------------------------------------------------------------------------------------");
                performDiagramTransformation(diagram);
                deleteMarkedClasses();
                writeCurlyBracketToTheEndOfAFile();
                showMessage("Diagram processing completed");
            }
        }
    }

    private void deleteMarkedClasses() {
        for (String className : classesToDelete) {
            paths.remove(Paths.get(pathToDir + "/" + diagram.getName() + "/" + className.toLowerCase() + "ID.json"));
            deleteFile(className);
        }
        classesToDelete.clear();
    }

    private void performDiagramTransformation(IDiagramUIModel diagram) {
        Iterator diagramElementIter = diagram.diagramElementIterator();
        while (diagramElementIter.hasNext()) {
            IDiagramElement diagramElement = (IDiagramElement) diagramElementIter.next();
            IModelElement modelElement = diagramElement.getModelElement();
            if (modelElement.getModelType().equals("Class")) {
                IClass iClass = (IClass) modelElement;
                performClassTransformation(iClass);
                showMessage("--------------------------------------------------------------------------------------------------------------------------------------");
            }
        }
    }

    private void performClassTransformation(IClass iClass) {
        Path path = Paths.get(pathToDir + "/" + diagram.getName() + "/" + iClass.getName().toLowerCase() + "ID.json");
        paths.add(path);
        showMessage("Class: " + iClass.getName());
        writeClassStereotypes(iClass, path);
        writeClassAttributes(iClass, path, true, "\n\t");
        writeClassGeneralization(iClass, path);
        if (!geneClasses.contains(iClass.getName()))
            writeClassAssociations(iClass, path, null);
    }

    private void writeClassGeneralization(IClass iClass, Path path) {
        Iterator genIter = iClass.fromRelationshipIterator();
        while (genIter.hasNext()) {
            IRelationship relationship = (IRelationship) genIter.next();
            IModelElement otherModel = relationship.getTo();
            if (otherModel.getModelType().equals("Class")) {
                IClass iClassSub = (IClass) otherModel;
                showMessage(iClassSub.getName());
                writeClassAttributes(iClassSub, path, false, "\n\t");
                writeClassAssociations(iClassSub, path, iClass);
                classesToDelete.add(iClassSub.getName());
            }
        }
    }

    private void writeClassStereotypes(IClass iClass, Path path) {
        Iterator stereotypeIter = iClass.stereotypeIterator();
        boolean enumeration = false;
        while (stereotypeIter.hasNext()) {
            String stereotype = (String) stereotypeIter.next();
            showMessage("Stereotype: " + stereotype);
            if (stereotype.equals("enumeration")) {
                Iterator enumLiteralIter = iClass.enumerationLiteralIterator();
                while (enumLiteralIter.hasNext()) {
                    IEnumerationLiteral enumLiteral = (IEnumerationLiteral) enumLiteralIter.next();
                    showMessage("EnumLit: " + enumLiteral.getName());
                }
                enumeration = true;
            }
        }
        /*if (enumeration)
            classesToDelete.add(iClass.getName());*/
    }

    private void showDirDialog() {
        viewManager = ApplicationManager.instance().getViewManager();
        Component parentFrame = viewManager.getRootFrame();
        JFileChooser fileChooser = viewManager.createJFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.showSaveDialog(parentFrame);
        File file = fileChooser.getSelectedFile();
        pathToDir = file.getAbsolutePath();
        showMessage("Path to directory: " + pathToDir);
    }

    private void deleteAllFilesInDir(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                deleteAllFilesInDir(file);
            if (file.getName().lastIndexOf(".") != -1)
                if (file.getName().substring(file.getName().lastIndexOf(".")).equals(".json"))
                    file.delete();
        }
    }

    private void deleteFile(String className) {
        try {
            Files.delete(Paths.get(pathToDir + "/" + diagram.getName() + "/" + className.toLowerCase() + "ID.json"));
        } catch (IOException e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            showMessage(errors.toString());
        }

    }

    private void writeCurlyBracketToTheNewFile(Path path) {
        File dir = new File(path.toString());
        dir.getParentFile().mkdirs();
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("{");
        } catch (IOException e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            showMessage(errors.toString());
        }
    }

    private void writeCurlyBracketToTheEndOfAFile() {
        for (Path path : paths) {
            writeToExistingFile("\n}", path, false);
            showMessage("Created: " + path.toString());
        }
        paths.clear();
    }

    private void writeToExistingFile(String text, Path path, boolean comma) {
        File f = new File(path.toString());
        if (f.exists() && !f.isDirectory()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString(), true))) {
                if (!text.equals("\n}") && comma)
                    writer.write("," + text);
                else
                    writer.write(text);
            } catch (IOException e) {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                showMessage(errors.toString());
            }
        } else {
            writeCurlyBracketToTheNewFile(path);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString(), true))) {
                writer.write(text);
            } catch (IOException e) {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                showMessage(errors.toString());
            }
        }
    }

    private void embedManyRelation(IClass iClass, Path path, IClass superClass, boolean composition) {
        if (superClass != null && geneClasses.contains(iClass.getName())) {
            showMessage("Wrote many objects of " + superClass.getName() + " into: " + path.toString());
            writeToExistingFile("\n\t\"" + iClass.getName().toLowerCase() + "s\": [", path, true);
            if (composition) {
                String compositionClassName = path.toString().substring(path.toString().lastIndexOf("\\")+1);
                compositionClassName = compositionClassName.lastIndexOf(".") > 0 ? compositionClassName.substring(0, compositionClassName.lastIndexOf(".")) : compositionClassName;
                writeToExistingFile("\n\t\t{\n\t\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + superClass.getName().toLowerCase() + "ID_"+ compositionClassName + "\"", path, false);
            } else {
                writeToExistingFile("\n\t\t{\n\t\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + superClass.getName().toLowerCase() + "ID\"", path, false);
            }
            writeClassAttributes(superClass, path, false, "\n\t\t\t");
            writeClassAttributes(iClass, path, false, "\n\t\t\t");
        } else {
            showMessage("Wrote many objects of " + iClass.getName() + " into: " + path.toString());
            writeToExistingFile("\n\t\"" + iClass.getName().toLowerCase() + "s\": [", path, true);
            if (composition) {
                String compositionClassName = path.toString().substring(path.toString().lastIndexOf("\\")+1);
                compositionClassName = compositionClassName.lastIndexOf(".") > 0 ? compositionClassName.substring(0, compositionClassName.lastIndexOf(".")) : compositionClassName;
                writeToExistingFile("\n\t\t{\n\t\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + iClass.getName().toLowerCase() + "ID_"+ compositionClassName + "\"", path, false);
            } else {
                writeToExistingFile("\n\t\t{\n\t\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + iClass.getName().toLowerCase() + "ID\"", path, false);
            }
            writeClassAttributes(iClass, path, false, "\n\t\t\t");
        }
        //writeToExistingFile("\n\t\t},\n\t\t{\n\t\t\t...\n\t\t}\n\t]", path, false);
        writeToExistingFile("\n\t\t}\n\t]", path, false);
    }

    private void embedOneRelation(IClass iClass, Path path, IClass superClass, boolean composition) {
        if (superClass != null && geneClasses.contains(iClass.getName())) {
            showMessage("Wrote one object of " + superClass.getName() + " into: " + path.toString());
            writeToExistingFile("\n\t\"" + iClass.getName().toLowerCase() + "\": {", path, true);
            if (composition) {
                String compositionClassName = path.toString().substring(path.toString().lastIndexOf("\\")+1);
                compositionClassName = compositionClassName.lastIndexOf(".") > 0 ? compositionClassName.substring(0, compositionClassName.lastIndexOf(".")) : compositionClassName;
                writeToExistingFile("\n\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + superClass.getName().toLowerCase() + "ID_"+ compositionClassName + "\"", path, false);
            } else {
                writeToExistingFile("\n\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + superClass.getName().toLowerCase() + "ID\"", path, false);
            }
            writeClassAttributes(superClass, path, false, "\n\t\t");
            writeClassAttributes(iClass, path, false, "\n\t\t");
        } else {
            showMessage("Wrote one object of " + iClass.getName() + " into: " + path.toString());
            writeToExistingFile("\n\t\"" + iClass.getName().toLowerCase() + "\": {", path, true);
            if (composition) {
                String compositionClassName = path.toString().substring(path.toString().lastIndexOf("\\")+1);
                compositionClassName = compositionClassName.lastIndexOf(".") > 0 ? compositionClassName.substring(0, compositionClassName.lastIndexOf(".")) : compositionClassName;
                writeToExistingFile("\n\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + iClass.getName().toLowerCase() + "ID_"+ compositionClassName + "\"", path, false);
            } else {
                writeToExistingFile("\n\t\t\"" + iClass.getName().toLowerCase() + "\": " + "\"" + iClass.getName().toLowerCase() + "ID\"", path, false);
            }
            writeClassAttributes(iClass, path, false, "\n\t\t");
        }
        writeToExistingFile("\n\t}", path, false);
    }

    private void createCompositedRelation(IClass iClass, IClass iClassAssoTo, String multiplicity) {
        Path path;
        if (multiplicity.equals("1"))
            path = Paths.get(pathToDir + "/" + diagram.getName() + "/" + iClass.getName().toLowerCase() + "ID_" + iClassAssoTo.getName().toLowerCase() + "ID.json");
        else
            path = Paths.get(pathToDir + "/" + diagram.getName() + "/" + iClass.getName().toLowerCase() + "ID_" + iClassAssoTo.getName().toLowerCase() + "ID.json");
        classesToDelete.add(iClass.getName());
        paths.add(path);
        writeClassAttributes(iClass, path, false, "\n\t");
    }

    private void getAssoGeneClasses(IProject project) {
        showMessage("--------------------------------------------------------------------------------------------------------------------------------------");
        showMessage("Finding association classes and generalizations in project");
        IDiagramUIModel diagram;
        Iterator genIter;
        Iterator diagramElementIter;
        Iterator diagramIter = project.diagramIterator();
        while (diagramIter.hasNext()) {
            diagram = (IDiagramUIModel) diagramIter.next();
            diagramElementIter = diagram.diagramElementIterator();
            while (diagramElementIter.hasNext()) {
                IDiagramElement diagramElement = (IDiagramElement) diagramElementIter.next();
                IModelElement modelElement = diagramElement.getModelElement();
                if (modelElement.getModelType().equals("Class")) {
                    IClass iClass = (IClass) modelElement;
                    genIter = iClass.fromRelationshipIterator();
                    while (genIter.hasNext()) {
                        IRelationship relationship = (IRelationship) genIter.next();
                        IModelElement otherModel = relationship.getTo();
                        if (otherModel.getModelType().equals("Association")) {
                            IAssociation association = (IAssociation) otherModel;
                            assoClasses.put(association, iClass);
                            showMessage("Association class - AssoClass: " + iClass.getName() + " AssoFrom: " + association.getFrom().getName() + " AssoTo: " + association.getTo().getName());
                        } else if (otherModel.getModelType().equals("Class")) {
                            showMessage("Generalization - Class: " + iClass.getName() + " SubClass: " + otherModel.getName());
                            IClass iClassSub = (IClass) otherModel;
                            geneClasses.add(iClassSub.getName());
                        }
                    }
                }
            }
        }
    }

    private void writeClassAttributes(IClass iClass, Path path, boolean verbose, String whiteChars) {
        String value = "";
        Iterator attributeIter = iClass.attributeIterator();
        while (attributeIter.hasNext()) {
            IAttribute attribute = (IAttribute) attributeIter.next();
            if (!attribute.isID()) {
                value = "";
                if (attribute.getType().toString().equals("String") || attribute.getType().toString().equals("DateTime") || attribute.getType().toString().equals("Date"))
                    value = "someString";
                else if (attribute.getType().getClass().getName().equals("v.bei.bau") && !attribute.getName().equals("agreed") && !attribute.getName().equals("gender"))
                    value = "someNumber";
                else if (attribute.getType().getClass().getName().equals("v.bei.bau"))
                    value = "someBoolean";
                writeToExistingFile(whiteChars + "\"" + attribute.getName() + "\": " + "\"" + value + "\"", path, true);
            }
            if (verbose)
                showMessage("Atr: " + attribute.getName());
        }
    }

    private void writeClassAssociations(IClass iClass, Path path, IClass superClass) {
        Path tempPath;
        boolean asso;
        Iterator assoIter = iClass.fromRelationshipEndIterator();
        while (assoIter.hasNext()) {
            IRelationshipEnd relationshipEndFrom = (IRelationshipEnd) assoIter.next();
            IAssociationEnd associationFrom = (IAssociationEnd) relationshipEndFrom;
            IRelationshipEnd relationshipEndTo = relationshipEndFrom.getEndRelationship().getToEnd();
            IAssociationEnd associationTo = (IAssociationEnd) relationshipEndTo;
            IClass iClassAssoTo = (IClass) associationTo.getModelElement();
            tempPath = Paths.get(pathToDir + "/" + diagram.getName() + "/" + iClassAssoTo.getName().toLowerCase() + "ID.json");
            if (((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("*"))
                    && ((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("*"))) {
                Iterator it = assoClasses.entrySet().iterator();
                asso = false;
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    IAssociation association = (IAssociation) pair.getKey();
                    if ((association.getFrom().getName().equals(iClass.getName()) || association.getFrom().getName().equals(iClassAssoTo.getName()))
                            && (association.getTo().getName().equals(iClass.getName()) || association.getTo().getName().equals(iClassAssoTo.getName()))) {
                        IClass iAssoClass = (IClass) pair.getValue();
                        iClass = (IClass) association.getFrom();
                        iClassAssoTo = (IClass) association.getTo();
                        tempPath = Paths.get(pathToDir + "/" + diagram.getName() + "/" + iAssoClass.getName().toLowerCase() + "ID.json");
                        embedOneRelation(iClass, tempPath, superClass, false);
                        embedOneRelation(iClassAssoTo, tempPath, superClass, false);
                        asso = true;
                    }
                }
                if (!asso) {
                    embedManyRelation(iClass, tempPath, superClass, false);
                    embedManyRelation(iClassAssoTo, path, superClass, false);
                }
            } else if (((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("*"))
                    && ((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("1"))) {
                embedManyRelation(iClass, tempPath, superClass, false);
                embedOneRelation(iClassAssoTo, path, superClass,false);
            } else if (((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("*"))
                    && ((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("1"))) {
                embedManyRelation(iClassAssoTo, path, superClass, false);
                embedOneRelation(iClass, tempPath, superClass, false);
            } else if (((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("1"))
                    && ((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("1"))) {
                embedOneRelation(iClass, tempPath, superClass, false);
                embedOneRelation(iClassAssoTo, path, superClass, false);
            } else if ((associationTo.getAggregationKind().equals("Composited") && (associationTo.getMultiplicity().equals("*") || associationTo.getMultiplicity().equals("1")))
                    && ((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("*"))) {
                embedManyRelation(iClass, tempPath, superClass, true);
                createCompositedRelation(iClass, iClassAssoTo, associationTo.getMultiplicity());
            } else if ((associationTo.getAggregationKind().equals("Composited") && (associationTo.getMultiplicity().equals("*") || associationTo.getMultiplicity().equals("1")))
                    && ((associationFrom.getAggregationKind().equals("None") || associationFrom.getAggregationKind().equals("Shared")) && associationFrom.getMultiplicity().equals("1"))) {
                embedOneRelation(iClass, tempPath, superClass, true);
                createCompositedRelation(iClass, iClassAssoTo, associationTo.getMultiplicity());
            } else if ((associationFrom.getAggregationKind().equals("Composited") && (associationFrom.getMultiplicity().equals("*") || associationFrom.getMultiplicity().equals("1")))
                    && ((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("*"))) {
                embedManyRelation(iClassAssoTo, path, superClass, true);
                createCompositedRelation(iClassAssoTo, iClass, associationFrom.getMultiplicity());
            } else if ((associationFrom.getAggregationKind().equals("Composited") && (associationFrom.getMultiplicity().equals("*") || associationFrom.getMultiplicity().equals("1")))
                    && ((associationTo.getAggregationKind().equals("None") || associationTo.getAggregationKind().equals("Shared")) && associationTo.getMultiplicity().equals("1"))) {
                embedOneRelation(iClassAssoTo, path, superClass, true);
                createCompositedRelation(iClassAssoTo, iClass, associationFrom.getMultiplicity());
            }
            showMessage("AssoFrom: " + iClass.getName() + " Mult: " + associationFrom.getMultiplicity() + " Type: " + associationFrom.getAggregationKind());
            showMessage("AssoTo: " + associationTo.getModelElement().getName() + " Mult: " + associationTo.getMultiplicity() + " Type: " + associationTo.getAggregationKind());
        }
    }

    private void showMessage(String text) {
        viewManager.showMessage(text, "umltonosqlplugin");
    }
}
