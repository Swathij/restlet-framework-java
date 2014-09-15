/**
 * Copyright 2005-2014 Restlet
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: Apache 2.0 or LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL
 * 1.0 (the "Licenses"). You can select the license that you prefer but you may
 * not use this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the Apache 2.0 license at
 * http://www.opensource.org/licenses/apache-2.0
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://restlet.com/products/restlet-framework
 * 
 * Restlet is a registered trademark of Restlet S.A.S.
 */

package org.restlet.ext.raml.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.raml.model.ActionType;
import org.raml.model.ParamType;
import org.raml.model.Raml;
import org.raml.model.Resource;
import org.raml.parser.rule.ValidationResult;
import org.raml.parser.visitor.RamlValidationService;
import org.restlet.ext.apispark.internal.model.Property;
import org.restlet.ext.apispark.internal.model.Representation;

import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema;
import com.fasterxml.jackson.module.jsonSchema.types.BooleanSchema;
import com.fasterxml.jackson.module.jsonSchema.types.IntegerSchema;
import com.fasterxml.jackson.module.jsonSchema.types.NumberSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.fasterxml.jackson.module.jsonSchema.types.SimpleTypeSchema;
import com.fasterxml.jackson.module.jsonSchema.types.StringSchema;

/**
 * Utility class for RAML java beans.
 * 
 * @author Cyprien Quilici
 */
public class RamlUtils {
    /**
     * The list of java types that correspond to RAML's integer type.
     */
    private static final List<String> integerTypesList = Arrays.asList(
            "integer", "int");

    /**
     * The list of java types that correspond to RAML's number type.
     */
    private static final List<String> numericTypesList = Arrays.asList(
            "integer", "int", "double", "long", "float");

    /**
     * Returns the String passed as a parameter with a capital first letter.
     * Used to generate resource names in camel case
     * 
     * @param str
     *            The string to process
     * @return The String with a capital first letter
     */
    public static String capFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return ("" + str.charAt(0)).toUpperCase() + str.substring(1);
    }

    /**
     * Generates the JsonSchema of a Representation's Property of primitive
     * type.
     * 
     * @param property
     *            The Property from which the JsonSchema is generated.
     * @return The JsonSchema of the given Property.
     */
    protected static SimpleTypeSchema generatePrimitiveSchema(Property property) {
        SimpleTypeSchema result = null;

        String name = property.getName();
        String type = (property.getType() != null) ? property.getType()
                .toLowerCase() : null;
        if (RamlUtils.integerTypesList.contains(type)) {
            IntegerSchema integerSchema = new IntegerSchema();
            integerSchema.setTitle(name);
            if (property.getMin() != null) {
                integerSchema.setMinimum(Double.parseDouble(property.getMin()));
            }
            if (property.getMax() != null) {
                integerSchema.setMaximum(Double.parseDouble(property.getMax()));
            }
            result = integerSchema;
        } else if (RamlUtils.numericTypesList.contains(type)) {
            NumberSchema numberSchema = new NumberSchema();
            numberSchema.setTitle(name);
            if (property.getMin() != null) {
                numberSchema.setMinimum(Double.parseDouble(property.getMin()));
            }
            if (property.getMax() != null) {
                numberSchema.setMaximum(Double.parseDouble(property.getMax()));
            }
            result = numberSchema;
        } else if ("boolean".equals(type)) {
            BooleanSchema booleanSchema = new BooleanSchema();
            booleanSchema.setTitle(name);
            result = booleanSchema;
        } else if ("string".equals(type) || "date".equals(type)) {
            StringSchema stringSchema = new StringSchema();
            stringSchema.setTitle(name);
            result = stringSchema;
        }

        return result;
    }

    /**
     * Generates the JsonSchema of a Representation.
     * 
     * @param representation
     *            The representation.
     * @return The JsonSchema of the Representation.
     */
    public static JsonSchema generateSchema(Representation representation) {
        SimpleTypeSchema propertySchema;
        ObjectSchema objectSchema;
        objectSchema = new ObjectSchema();
        objectSchema.setTitle(representation.getName());
        objectSchema.setDescription(representation.getDescription());
        if (!representation.isRaw()) {
            if (representation.getExtendedType() != null) {
                JsonSchema[] extended = new JsonSchema[1];
                SimpleTypeSchema typeExtended = new ObjectSchema();
                typeExtended.set$ref(representation.getExtendedType());
                extended[0] = typeExtended;
                objectSchema.setExtends(extended);
            }
            objectSchema.setProperties(new HashMap<String, JsonSchema>());
            for (Property property : representation.getProperties()) {
                if (property.getMaxOccurs() != 1) {
                    ArraySchema array = new ArraySchema();
                    array.setTitle(property.getName());
                    array.setRequired(property.getMinOccurs() > 0);
                    array.setUniqueItems(property.isUniqueItems());
                    if (isPrimitiveType(property.getType())) {
                        Property prop = new Property();
                        prop.setName(property.getName());
                        prop.setType(property.getType());
                        array.setItemsSchema(generatePrimitiveSchema(prop));
                    } else {
                        SimpleTypeSchema reference = new ObjectSchema();
                        reference.set$ref("#/schemas/" + property.getType());
                        array.setItemsSchema(reference);
                        // array.setItemsSchema(generateSchema(RamlTranslator
                        // .getRepresentationByName(representations,
                        // property.getType()), representations));
                    }
                    objectSchema.getProperties().put(array.getTitle(), array);
                } else if (!isPrimitiveType(property.getType())) {
                    propertySchema = new ObjectSchema();
                    propertySchema.setTitle(property.getName());
                    propertySchema.set$ref("#/schemas/" + property.getType());
                    propertySchema.setRequired(property.getMinOccurs() > 0);
                    objectSchema.getProperties().put(propertySchema.getTitle(),
                            propertySchema);
                } else {
                    SimpleTypeSchema primitive = generatePrimitiveSchema(property);
                    primitive.setRequired(property.getMinOccurs() > 0);
                    if (property.getDefaultValue() != null) {
                        primitive.setDefault(property.getDefaultValue());
                    }
                    objectSchema.getProperties().put(property.getName(),
                            primitive);
                }
            }

        }
        return objectSchema;
    }

    /**
     * Returns the RAML {@link ActionType} given an HTTP method name.
     * 
     * @param method
     *            The HTTP method name as String.
     * @return The corresponding {@link ActionType}.
     */
    public static ActionType getActionType(String method) {
        String m = (method != null) ? method.toLowerCase() : null;
        if ("post".equals(m)) {
            return ActionType.POST;
        } else if ("get".equals(m)) {
            return ActionType.GET;
        } else if ("put".equals(m)) {
            return ActionType.PUT;
        } else if ("patch".equals(m)) {
            return ActionType.PATCH;
        } else if ("delete".equals(m)) {
            return ActionType.DELETE;
        } else if ("head".equals(m)) {
            return ActionType.HEAD;
        } else if ("options".equals(m)) {
            return ActionType.OPTIONS;
        } else if ("trace".equals(m)) {
            return ActionType.TRACE;
        }
        return null;
    }

    /**
     * Returns the RAML parameter type given a java primitive type.
     * 
     * @param type
     *            The Java type.
     * @return The RAML parameter type.
     */
    public static ParamType getParamType(String type) {
        String t = (type != null) ? type.toLowerCase() : null;

        if (integerTypesList.contains(t)) {
            return ParamType.INTEGER;
        } else if (numericTypesList.contains(t)) {
            return ParamType.NUMBER;
        } else if ("boolean".equals(t)) {
            return ParamType.BOOLEAN;
        } else if ("date".equals(t)) {
            return ParamType.DATE;
        }

        // TODO add files
        // else if () {
        // return ParamType.FILE;
        // }
        return ParamType.STRING;
    }

    /**
     * Gets the parent resource of a Resource given its path and the list of
     * paths available on the API.
     * 
     * @param paths
     *            The list of paths available on the API.
     * @param resourcePath
     *            The path of the resource the parent resource is searched for.
     * @param raml
     *            The RAML representing the API.
     * @return The parent resource.
     */
    public static Resource getParentResource(List<String> paths,
            String resourcePath, Raml raml) {
        List<String> parentPaths = new ArrayList<String>();
        parentPaths.addAll(paths);
        parentPaths.add(resourcePath);
        Collections.sort(parentPaths);
        int index = parentPaths.indexOf(resourcePath);
        if (index != 0) {
            String parentPath = parentPaths.get(index - 1);
            if (resourcePath.startsWith(parentPath)) {
                return getResourceByCompletePath(raml, parentPath);
            }
        }
        return null;
    }

    /**
     * Returns a RAML Resource given its complete path.
     * 
     * @param raml
     *            The RAML in which the Resource is searched for.
     * @param path
     *            The complete path of the resource.
     * @return The Resource.
     */
    private static Resource getResourceByCompletePath(Raml raml, String path) {
        for (Entry<String, Resource> entry : raml.getResources().entrySet()) {
            if (path.equals(entry.getValue().getParentUri()
                    + entry.getValue().getRelativeUri())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Indicates if the given type is a primitive type.
     * 
     * @param type
     *            The type to check.
     * @return True if the given type is primitive, false otherwise.
     */
    public static boolean isPrimitiveType(String type) {
        String t = (type != null) ? type.toLowerCase() : null;
        return ("string".equals(t) || "int".equals(t) || "integer".equals(t)
                || "long".equals(t) || "float".equals(t) || "double".equals(t)
                || "date".equals(t) || "boolean".equals(t) || "bool".equals(t));
    }

    /**
     * Generates a name for a resource computed from its path. The name is
     * composed of all alphanumeric characters in camel case.<br/>
     * Ex: /contacts/{contactId} => ContactsContactId
     * 
     * @param uri
     *            The URI of the Resource
     * @return The Resource's name computed from the path.
     */
    public static String processResourceName(String uri) {
        String processedUri = "";
        String[] split = uri.replaceAll("\\{", "").replaceAll("\\}", "")
                .split("/");
        for (String str : split) {
            processedUri += RamlUtils.capFirst(str);
        }
        return processedUri;
    }

    /**
     * Returns the primitive type as RAML expects them.
     * 
     * @param type
     *            The Java primitive type.
     * @return The primitive type expected by RAML.
     */
    public static String toRamlType(String type) {
        if ("Integer".equals(type)) {
            return "int";
        } else if ("String".equals(type)) {
            return "string";
        } else if ("Boolean".equals(type)) {
            return "boolean";
        }

        return type;
    }

    /**
     * Indicates if the given RAML definition is valid according to RAML
     * specifications.
     * 
     * @param raml
     *            The RAML definition.
     * @throws TranslationException
     */
    public static List<ValidationResult> validate(String location)
            throws TranslationException {
        // TODO see if needed as it requires lots of dependencies.
        return RamlValidationService.createDefault().validate(location);
    }
}
