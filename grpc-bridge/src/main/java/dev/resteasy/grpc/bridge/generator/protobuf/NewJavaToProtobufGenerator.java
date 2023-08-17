package dev.resteasy.grpc.bridge.generator.protobuf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedClassDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Log;
import com.github.javaparser.utils.SourceRoot;

import dev.resteasy.grpc.bridge.generator.GenerationContext;
import dev.resteasy.grpc.bridge.generator.Generator;
import dev.resteasy.grpc.bridge.runtime.servlet.HttpServletRequestImpl;

/**
 * Traverses a set of Jakarta REST resources and creates a protobuf representation.
 * <p/>
 * <ol>
 * <li>Find all Jakarta REST resource methods and resource locators and create an rpc entry for each</li>
 * <li>Find the transitive closure of the classes mentioned in the resource methods and locators
 * and create a message entry for each.</li>
 * </ol>
 * <p/>
 * </pre>
 * For example,
 * <p/>
 *
 * <pre>
 * public class CC1 {
 *
 *     &#064;Path("m1")
 *     &#064;GET
 *     String m1(CC2 cc2) {
 *         return "x";
 *     }
 *
 *     String m2(String s) {
 *         return "x";
 *     }
 *
 *     &#064;Path("m3")
 *     &#064;GET
 *     String m3(CC4 cc4) {
 *         return "x";
 *     }
 * }
 * </pre>
 *
 * together with the class definitions
 * <p/>
 *
 * <pre>
 * package io.grpc.examples;
 *
 * public class CC2 extends CC3 {
 *    int j;
 *
 *    public CC2(String s, int j) {
 *       super(s);
 *       this.j = j;
 *    }
 *
 *    public CC2() {}
 * }
 *
 * ========================
 * package io.grpc.examples;
 *
 * public class CC3 {
 *    String s;
 *
 *    public CC3(String s) {
 *       this.s = s;
 *    }
 *
 *    public CC3() {}
 * }
 *
 * ========================
 * package io.grpc.examples;
 *
 * public class CC4 {
 *    private String s;
 *    private CC5 cc5;
 *
 *    public CC4(String s, CC5 cc5) {
 *       this.s = s;
 *       this.cc5 = cc5;
 *    }
 *
 *    public CC4() {}
 * }
 *
 * ========================
 * package io.grpc.examples;
 *
 * public class CC5 {
 *    int k;
 *
 *    public CC5(int k) {
 *       this.k = k;
 *    }
 *
 *    public CC5() {}
 * }
 * </pre>
 *
 * is translated to CC1.proto:
 * <p/>
 *
 * <pre>
 * syntax = "proto3";
 * package io.grpc.examples;
 * option java_package = "io.grpc.examples";
 * option java_outer_classname = "CC1_proto";
 *
 * service CC1Service {
 *    rpc m1 (GeneralEntityMessage) returns (GeneralReturnMessage);
 *    rpc m3 (GeneralEntityMessage) returns (GeneralReturnMessage);
 * }
 *
 * message io_grpc_examples___CC2 {
 *    int32 j = 1;
 *    io_grpc_examples___CC3 cC3___super = 2;
 * }
 *
 * message io_grpc_examples___CC4 {
 *    string s = 1;
 *    io_grpc_examples___CC5 cc5 = 2;
 * }
 *
 * message io_grpc_examples___CC3 {
 *    string s = 1;
 * }
 *
 * message io_grpc_examples___CC5 {
 *    int32 k = 1;
 * }
 *
 * ...
 *
 * message GeneralEntityMessage {
 *    ServletInfo servletInfo = 1;
 *    string URL = 2;
 *    map<string, gHeader> headers = 3;
 *    repeated gCookie cookies = 4;
 *    string httpMethod = 5;
 *    oneof messageType {
 *       io_grpc_examples___CC4 io_grpc_examples___CC4_field = 6;
 *       io_grpc_examples___CC2 io_grpc_examples___CC2_field = 7;
 *       FormMap form_field = 8;
 *    }
 * }
 *
 * message GeneralReturnMessage {
 *    map<string, gHeader> headers = 1;
 *    repeated gNewCookie cookies = 2;
 *    gInteger status = 3;
 *    oneof messageType {
 *       gString gString_field = 4;
 *    }
 * }
 * </pre>
 * <p/>
 * <b>Notes.</b>
 * <ol>
 * <li>{@code CC1.m2()} is not a resource method, so it does not appear in CC1.proto.
 * <li>Protobuf syntax does not support inheritance, so {@code JavaToProtobufGenerator}
 * treats a superclass as a special field. For example, {@code CC2} is a subclass of {@code CC3},
 * so each instance of {@code CC2} has a field named {@code cC3___super} of {@code type io_grpc_examples___CC3}.
 * <li>{@code GeneralEntityMessage} and {@code GeneralReturnMessage} are general purpose classes for conveying
 * entity parameters to the server and responses back to the client. They are defined to hold all possible entity
 * and return types plus a variety of additional fields. For more information, see the User Guide.
 * </ol>
 */
@SuppressWarnings("DuplicatedCode")
public class NewJavaToProtobufGenerator implements Generator {

    private static final Logger logger = Logger.getLogger(NewJavaToProtobufGenerator.class);
    private static final String LS = System.lineSeparator();

    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("boolean", "bool"),
            Map.entry("byte", "int32"),
            Map.entry("short", "int32"),
            Map.entry("int", "int32"),
            Map.entry("long", "int64"),
            Map.entry("float", "float"),
            Map.entry("double", "double"),
            Map.entry("char", "int32"),
            Map.entry("String", "string"),
            Map.entry("java.lang.String", "string"));
    private static final Map<String, String> PRIMITIVE_WRAPPER_TYPES = Map.ofEntries(
            Map.entry("boolean", "gBoolean"),
            Map.entry("byte", "gByte"),
            Map.entry("short", "gShort"),
            Map.entry("int", "gInteger"),
            Map.entry("long", "gLong"),
            Map.entry("float", "gFloat"),
            Map.entry("double", "gDouble"),
            Map.entry("char", "gCharacter"),
            Map.entry("string", "gString"),
            Map.entry("Boolean", "gBoolean"),
            Map.entry("Byte", "gByte"),
            Map.entry("Short", "gShort"),
            Map.entry("Integer", "gInteger"),
            Map.entry("Long", "gLong"),
            Map.entry("Float", "gFloat"),
            Map.entry("Double", "gDouble"),
            Map.entry("Character", "gCharacter"),
            Map.entry("String", "gString"),
            Map.entry("java.lang.String", "gString"),
            Map.entry("java.lang.Byte", "gByte"),
            Map.entry("java.lang.Short", "gShort"),
            Map.entry("java.lang.Integer", "gInteger"),
            Map.entry("java.lang.Long", "gLong"),
            Map.entry("java.lang.Float", "gFloat"),
            Map.entry("java.lang.Double", "gDouble"),
            Map.entry("java.lang.Boolean", "gBoolean"),
            Map.entry("java.lang.Character", "gCharacter"));
    private static final Map<String, String> PRIMITIVE_WRAPPER_DEFINITIONS = Map.ofEntries(
            Map.entry("Boolean", "message gBoolean   {bool   value = $V$;}"),
            Map.entry("Byte", "message gByte      {int32  value = $V$;}"),
            Map.entry("Short", "message gShort     {int32  value = $V$;}"),
            Map.entry("Integer", "message gInteger   {int32  value = $V$;}"),
            Map.entry("Long", "message gLong      {int64  value = $V$;}"),
            Map.entry("Float", "message gFloat     {float  value = $V$;}"),
            Map.entry("Double", "message gDouble    {double value = $V$;}"),
            Map.entry("Character", "message gCharacter {string value = $V$;}"),
            Map.entry("String", "message gString    {string value = $V$;}"));
    private static final Set<String> ANNOTATIONS = Set.of("Context", "CookieParam", "HeaderParam", "MatrixParam", "PathParam",
            "QueryParam");
    private static final Set<String> HTTP_VERBS = Set.of("DELETE", "HEAD", "GET", "OPTIONS", "PATCH", "POST", "PUT");
    private static boolean needEmpty = false;
    private static boolean started = false;
    private static int counter = 1;
    private static boolean isSSE;
    private static final String SSE_EVENT_CLASSNAME = "dev_resteasy_grpc_bridge_runtime_sse___SseEvent";
    private final List<ResolvedReferenceTypeDeclaration> resolvedTypes = new CopyOnWriteArrayList<>();
    private final Set<String> entityMessageTypes = new HashSet<>();
    private final Set<String> returnMessageTypes = new HashSet<>();
    private final Set<String> visited = new HashSet<>();
    private final ClassVisitor classVisitor = new ClassVisitor();
    private final Set<String> additionalClasses = ConcurrentHashMap.newKeySet();

    @Override
    public void generate(final GenerationContext context) throws IOException {
        final Path dir = context.sourceDirectory();
        // TODO (jrp) replace this with something better, e.g. with the maven plugin we should use the Maven logger.
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());

        // SourceRoot is a tool that read and writes Java files from packages on a certain root directory.
        final SourceRoot sourceRoot = new SourceRoot(dir);
        final JavaSymbolSolver symbolSolver = getJavaSymbolSolver(dir);
        sourceRoot.getParserConfiguration().setSymbolResolver(symbolSolver);
        final List<ParseResult<CompilationUnit>> results = sourceRoot.tryToParseParallelized();
        final StringBuilder sb = new StringBuilder();
        protobufHeader(context, sb);
        // TODO (jrp) should we be using a stream here? Might be overkill instead of simply looping.
        final JakartaRESTResourceVisitor visitor = new JakartaRESTResourceVisitor(context.className());
        results.stream()
                .map(ParseResult::getResult)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(compilationUnit -> visitor.visit(compilationUnit, sb));
        if (started) {
            sb.append("}").append(LS);
        }
        processAdditionalClasses(symbolSolver, sb);
        while (!resolvedTypes.isEmpty()) {
            for (ResolvedReferenceTypeDeclaration rrtd : resolvedTypes) {
                classVisitor.visit(rrtd, sb);
            }
        }
        finishProto(sb);
        writeProtoFile(context, sb);
    }

    private static JavaSymbolSolver getJavaSymbolSolver(final Path dir) {
        final TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
        final TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(dir);
        final CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(reflectionTypeSolver);
        combinedTypeSolver.add(javaParserTypeSolver);
        // TODO (jrp) add additional libraries from dependencies, e.g. <includes>
        return new JavaSymbolSolver(combinedTypeSolver);
    }

    private static void protobufHeader(final GenerationContext context, final StringBuilder sb) {
        sb.append("syntax = \"proto3\";").append(LS);
        sb.append("package ").append(context.packageName().replace('-', '.')).append(";").append(LS);
        sb.append("import \"google/protobuf/any.proto\";").append(LS);
        sb.append("import \"google/protobuf/timestamp.proto\";").append(LS);
        sb.append("option java_package = \"").append(context.packageName()).append("\";").append(LS);
        sb.append("option java_outer_classname = \"").append(context.className()).append("_proto\";").append(LS);
    }

    /****************************************************************************/
    /******************************
     * primary methods *****************************
     * /
     *****************************************************************************/

    private void processAdditionalClasses(JavaSymbolSolver symbolSolver, StringBuilder sb) throws FileNotFoundException {
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
        while (!additionalClasses.isEmpty()) {
            for (String filename : additionalClasses) {
                int n = filename.indexOf(":");
                if (n < 0) {
                    throw new RuntimeException("bad syntax: " + filename);
                }
                String dir = filename.substring(0, n).trim();
                filename = dir + "/" + filename.substring(n + 1).replace(".", "/") + ".java";
                CompilationUnit cu = StaticJavaParser.parse(new File(filename));
                AdditionalClassVisitor additionalClassVisitor = new AdditionalClassVisitor(dir);
                additionalClassVisitor.visit(cu, sb);
            }
        }
        if (isSSE) {
            sb.append(LS).append("message dev_resteasy_grpc_bridge_runtime_sse___SseEvent {").append(LS)
                    .append("  string comment = ").append(counter++).append(";").append(LS)
                    .append("  string id = ").append(counter++).append(";").append(LS)
                    .append("  string name = ").append(counter++).append(";").append(LS)
                    .append("  google.protobuf.Any data = ").append(counter++).append(";").append(LS)
                    .append("  int64 reconnectDelay = ").append(counter++).append(";").append(LS)
                    .append("}").append(LS);
        }
    }

    private void finishProto(StringBuilder sb) {
        if (needEmpty) {
            sb.append(LS).append("message gEmpty {}");
            entityMessageTypes.add("gEmpty");
            returnMessageTypes.add("gEmpty");
        }

        for (String wrapper : PRIMITIVE_WRAPPER_DEFINITIONS.values()) {
            counter = 1;
            sb.append(LS).append(wrapper.replace("$V$", String.valueOf(counter++)));
        }
        createGeneralEntityMessageType(sb);
        createGeneralReturnMessageType(sb);
    }

    private void createGeneralEntityMessageType(StringBuilder sb) {
        counter = 1;
        sb.append(LS)
                .append(LS)
                .append("message gHeader {")
                .append(LS)
                .append("   repeated string values = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("}");
        counter = 1;
        sb.append(LS).append(LS).append("message gCookie {").append(LS)
                .append("   string name = ").append(counter++).append(";").append(LS)
                .append("   string value = ").append(counter++).append(";").append(LS)
                .append("   int32  version = ").append(counter++).append(";").append(LS)
                .append("   string path = ").append(counter++).append(";").append(LS)
                .append("   string domain = ").append(counter++).append(";").append(LS)
                .append("}");
        counter = 1;
        sb.append(LS)
                .append(LS)
                .append("message gNewCookie {")
                .append(LS)
                .append("   string name = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string value = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   int32  version = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string path = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string domain = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string comment = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   int32 maxAge = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   google.protobuf.Timestamp expiry = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   bool secure = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   bool httpOnly = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append(LS)
                .append("   enum SameSite {")
                .append(LS)
                .append("      NONE   = 0;")
                .append(LS)
                .append("      LAX    = 1;")
                .append(LS)
                .append("      STRICT = 2;")
                .append(LS).append("   }").append(LS).append(LS)
                .append("   SameSite sameSite = ").append(counter++).append(";").append(LS)
                .append("}");
        counter = 1;
        sb.append(LS).append(LS).append("message ServletInfo {").append(LS)
                .append("   string characterEncoding = ").append(counter++).append(";").append(LS)
                .append("   string clientAddress = ").append(counter++).append(";").append(LS)
                .append("   string clientHost = ").append(counter++).append(";").append(LS)
                .append("   int32  clientPort = ").append(counter++).append(";").append(LS)
                .append("}");
        counter = 1;
        sb.append(LS).append(LS).append("message FormValues {").append(LS)
                .append("   repeated string formValues_field = ").append(counter++).append(";").append(LS)
                .append("}");
        counter = 1;
        sb.append(LS).append(LS).append("message FormMap {").append(LS)
                .append("   map<string, FormValues> formMap_field = ").append(counter++).append(";").append(LS)
                .append("}");
        counter = 1;
        sb.append(LS)
                .append(LS)
                .append("message GeneralEntityMessage {")
                .append(LS)
                .append("   ServletInfo servletInfo = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string URL = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   map<string, gHeader> headers = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   repeated gCookie cookies = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   string httpMethod = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   oneof messageType {")
                .append(LS);
        for (String messageType : entityMessageTypes) {
            sb.append("      ")
                    .append(messageType)
                    .append(" ")
                    .append(namify(messageType)).append("_field")
                    .append(" = ")
                    .append(counter++).append(";").append(LS);
        }
        sb.append("      FormMap form_field = ").append(counter++).append(";").append(LS);
        sb.append("   }").append(LS).append("}").append(LS);
    }

    private void createGeneralReturnMessageType(StringBuilder sb) {
        counter = 1;
        sb.append(LS)
                .append("message GeneralReturnMessage {")
                .append(LS)
                .append("   map<string, gHeader> headers = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   repeated gNewCookie cookies = ")
                .append(counter++)
                .append(";")
                .append(LS)
                .append("   gInteger status = ")
                .append(counter++).append(";").append(LS)
                .append("   oneof messageType {")
                .append(LS);
        for (String messageType : returnMessageTypes) {
            sb.append("      ")
                    .append(messageType)
                    .append(" ")
                    .append(namify(messageType)).append("_field")
                    .append(" = ")
                    .append(counter++).append(";").append(LS);
        }
        sb.append("   }").append(LS).append("}").append(LS);
    }

    private static void writeProtoFile(final GenerationContext context, final StringBuilder sb) throws IOException {
        final Path path = Files.createDirectories(context.targetDirectory().resolve("proto"));
        if (Files.exists(path.resolve(context.className() + ".proto"))) {
            return;
        }
        Files.writeString(path.resolve(context.className() + ".proto"), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Visits each class in the transitive closure of all classes referenced in the
     * signatures of resource methods. Creates a service with an rpc declaration for
     * each resource method or locator.
     */
    private class JakartaRESTResourceVisitor extends VoidVisitorAdapter<StringBuilder> {
        private final String prefix;

        private JakartaRESTResourceVisitor(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void visit(final ClassOrInterfaceDeclaration subClass, final StringBuilder sb) {
            // Don't process gRPC server
            if (subClass.getFullyQualifiedName().orElse("").startsWith("grpc.server")) {
                return;
            }
            Optional<AnnotationExpr> pathAnnotation = subClass.getAnnotationByName("Path");
            SingleMemberAnnotationExpr annotationExpr = (SingleMemberAnnotationExpr) pathAnnotation.orElse(null);
            String classPath = "";
            if (annotationExpr != null) {
                classPath = annotationExpr.getMemberValue().toString();
                classPath = classPath.substring(1, classPath.length() - 1);
            }
            for (BodyDeclaration<?> bd : subClass.getMembers()) {
                if (bd instanceof MethodDeclaration) {
                    MethodDeclaration md = (MethodDeclaration) bd;
                    if (!isResourceOrLocatorMethod(md)) {
                        continue;
                    }
                    String methodPath = "";
                    pathAnnotation = md.getAnnotationByName("Path");
                    annotationExpr = (SingleMemberAnnotationExpr) pathAnnotation.orElse(null);
                    if (annotationExpr != null) {
                        methodPath = annotationExpr.getMemberValue().toString();
                        methodPath = methodPath.substring(1, methodPath.length() - 1);
                    }
                    String httpMethod = getHttpMethod(md);
                    // Add service with a method for each resource method in class.
                    if (!started) {
                        sb.append(LS).append("service ")
                                .append(prefix).append("Service {").append(LS);
                        started = true;
                    }
                    String entityType = getEntityParameter(md, httpMethod);
                    String returnType = getReturnType(md, httpMethod);
                    String syncType = isSuspended(md) ? "suspended"
                            : (isCompletionStage(md) ? "completionStage" : (isSSE(md) ? "sse" : "sync"));
                    isSuspended(md);
                    sb.append("// ");
                    if (!(classPath.isEmpty())) {
                        sb.append(classPath).append("/");
                    }
                    sb.append(methodPath).append(" ")
                            .append(entityType).append(" ")
                            .append(returnType).append(" ")
                            .append(httpMethod).append(" ")
                            .append(syncType).append(LS);
                    entityMessageTypes.add(entityType);
                    returnMessageTypes.add(returnType);
                    sb.append("  rpc ")
                            .append(md.getNameAsString())
                            .append(" (")
                            .append("GeneralEntityMessage")
                            .append(") returns (")
                            .append("sse".equals(syncType) ? "stream " : "")
                            .append("sse".equals(syncType) ? SSE_EVENT_CLASSNAME : "GeneralReturnMessage")
                            .append(");")
                            .append(LS);

                    // Add each parameter and return type to resolvedTypes for further processing.
                    for (Parameter p : md.getParameters()) {
                        if (!isEntity(p)) {
                            continue;
                        }
                        if (p.getType().resolve().isPrimitive()) {
                            continue;
                        }
                        ReferenceTypeImpl rt = (ReferenceTypeImpl) p.getType().resolve();
                        ResolvedReferenceTypeDeclaration rrtd = rt.getTypeDeclaration().get();
                        String type = rt.asReferenceType().getQualifiedName();
                        if (!visited.contains(type)) {
                            resolvedTypes.add(rrtd);
                        }
                    }
                }
            }
        }
    }

    /**
     * Visit all classes discovered by JakartaRESTResourceVisitor in the process of visiting all Jakarta REST resources
     */
    private class ClassVisitor extends VoidVisitorAdapter<StringBuilder> {

        /**
         * For each class, create a message type with a field for each variable in the class.
         */
        public void visit(ResolvedReferenceTypeDeclaration clazz, StringBuilder sb) {
            resolvedTypes.remove(clazz);
            if (clazz.isInterface()) {
                return;
            }
            if (clazz.getPackageName().startsWith("java")) {
                return;
            }
            if (PRIMITIVE_WRAPPER_DEFINITIONS.containsKey(clazz.getClassName())) {
                return;
            }
            if (Response.class.getName().equals(clazz.getQualifiedName())) {
                return;
            }
            String fqn = clazz.getQualifiedName();
            if (visited.contains(fqn)) {
                return;
            }
            visited.add(fqn);
            counter = 1;

            // Begin protobuf message definition.
            sb.append(LS).append("message ").append(fqnifyClass(fqn, isInnerClass(clazz))).append(" {").append(LS);

            // Scan all variables in class.
            for (ResolvedFieldDeclaration rfd : clazz.getDeclaredFields()) {
                String type = null;
                if (rfd.getType().isPrimitive() || rfd.getType().isReferenceType()
                        && String.class.getName().equals(rfd.getType().asReferenceType().getQualifiedName())) {
                    type = TYPE_MAP.get(rfd.getType().describe());
                } else if (rfd.getType() instanceof ResolvedArrayType) {
                    ResolvedArrayType rat = (ResolvedArrayType) rfd.getType();
                    ResolvedType ct = rat.getComponentType();
                    if ("byte".equals(ct.describe())) {
                        type = "bytes";
                    } else if (ct.isPrimitive()) {
                        type = "repeated " + TYPE_MAP.get(removeTypeVariables(ct.describe()));
                    } else {
                        fqn = removeTypeVariables(ct.describe());
                        if (!ct.isReferenceType()) {
                            continue;
                        }
                        if (!visited.contains(fqn)) {
                            resolvedTypes.add(ct.asReferenceType().getTypeDeclaration().get());
                        }
                        type = "repeated " + fqnifyClass(fqn, isInnerClass(ct.asReferenceType()
                                .getTypeDeclaration()
                                .get()));
                    }
                } else { // Defined type
                    if (rfd.getType().isReferenceType()) {
                        ResolvedReferenceTypeDeclaration rrtd = (ResolvedReferenceTypeDeclaration) rfd.getType()
                                .asReferenceType().getTypeDeclaration().get();
                        fqn = rrtd.getPackageName() + "." + rrtd.getClassName();
                        if (!visited.contains(fqn)) {
                            resolvedTypes.add(rrtd);
                        }
                        type = fqnifyClass(fqn, isInnerClass(rrtd));
                    } else if (rfd.getType().isTypeVariable()) {
                        type = "bytes ";
                    }
                }
                if (type != null) {
                    sb.append("  ")
                            .append(type)
                            .append(" ")
                            .append(rfd.getName())
                            .append(" = ")
                            .append(counter++).append(";").append(LS);
                }
            }

            // Add field for superclass.
            for (ResolvedReferenceType rrt : clazz.getAncestors()) {
                if (rrt.getTypeDeclaration().get() instanceof ReflectionClassDeclaration) {
                    ReflectionClassDeclaration rcd = (ReflectionClassDeclaration) rrt.getTypeDeclaration().get();
                    if (Object.class.getName().equals(rcd.getQualifiedName())) {
                        continue;
                    }
                    fqn = fqnifyClass(rcd.getPackageName() + "." + rcd.getName(), isInnerClass(rrt.getTypeDeclaration()
                            .get()));
                    if (!visited.contains(fqn)) {
                        resolvedTypes.add(rcd);
                    }
                    String superClassName = rcd.getName();
                    String superClassVariableName = Character.toString(Character.toLowerCase(superClassName.charAt(0)))
                            .concat(superClassName.substring(1)) + "___super";
                    sb.append("  ")
                            .append(fqn)
                            .append(" ")
                            .append(superClassVariableName)
                            .append(" = ")
                            .append(counter++).append(";").append(LS);
                    break;
                } else if (rrt.getTypeDeclaration().get() instanceof JavaParserClassDeclaration) {
                    JavaParserClassDeclaration jpcd = (JavaParserClassDeclaration) rrt.getTypeDeclaration().get();
                    ResolvedClassDeclaration rcd = jpcd.asClass();
                    if (Object.class.getName().equals(rcd.getClassName())) {
                        continue;
                    }
                    fqn = rcd.getPackageName() + "." + rcd.getName();
                    if (!visited.contains(fqn)) {
                        resolvedTypes.add(rcd);
                    }
                    fqn = fqnifyClass(fqn, isInnerClass(rrt.getTypeDeclaration().get()));
                    String superClassName = rcd.getName();
                    String superClassVariableName = Character.toString(Character.toLowerCase(superClassName.charAt(0)))
                            .concat(superClassName.substring(1)) + "___super";
                    sb.append("  ")
                            .append(fqn)
                            .append(" ")
                            .append(superClassVariableName)
                            .append(" = ")
                            .append(counter++).append(";").append(LS);
                    break;

                }
            }
            sb.append("}").append(LS);
        }
    }

    /**
     * Visit all classes discovered by JakartaRESTResourceVisitor in the process of visiting all Jakarta REST resources
     */
    private class AdditionalClassVisitor extends VoidVisitorAdapter<StringBuilder> {
        private String dir;

        AdditionalClassVisitor(final String dir) {
            this.dir = dir;
        }

        /**
         * For each class, create a message type with a field for each variable in the class.
         */
        public void visit(ClassOrInterfaceDeclaration clazz, StringBuilder sb) {
            if (PRIMITIVE_WRAPPER_DEFINITIONS.containsKey(clazz.getName().asString())) {
                return;
            }
            String packageName = getPackageName(clazz);
            String fqn = packageName + "." + clazz.getNameAsString();
            String filename = dir + ":" + fqn;
            additionalClasses.remove(filename);

            if (visited.contains(fqn)) {
                return;
            }
            visited.add(fqn);
            counter = 1;

            // Begin protobuf message definition.
            sb.append(LS + "message ").append(fqnifyClass(fqn, isInnerClass(clazz))).append(" {" + LS);

            // Scan all variables in class.
            for (FieldDeclaration fd : clazz.getFields()) {
                ResolvedFieldDeclaration rfd = fd.resolve();
                ResolvedType type = rfd.getType();
                String typeName = type.describe();
                if (TYPE_MAP.containsKey(typeName)) {
                    typeName = TYPE_MAP.get(typeName);
                } else if (type.isArray()) {
                    ResolvedType ct = type.asArrayType().getComponentType();
                    if ("byte".equals(ct.describe())) {
                        typeName = "bytes";
                    } else if (ct.isPrimitive()) {
                        typeName = "repeated " + typeName;
                    } else {
                        fqn = type.describe();
                        additionalClasses.add(dir + ":" + fqn);
                        typeName = "repeated "
                                + fqnifyClass(fqn, isInnerClass(type.asReferenceType().getTypeDeclaration().get()));
                    }
                } else { // Defined type
                    fqn = type.describe();
                    additionalClasses.add(dir + ":" + fqn);
                    typeName = fqnifyClass(type.describe(), isInnerClass(type.asReferenceType()
                            .getTypeDeclaration()
                            .get()));
                }
                if (type != null) {
                    sb.append("  ")
                            .append(typeName)
                            .append(" ")
                            .append(rfd.getName())
                            .append(" = ")
                            .append(counter++).append(";").append(LS);
                }
            }

            // Add field for superclass.
            for (ResolvedReferenceType rrt : clazz.resolve().getAllAncestors()) {
                if (Object.class.getName().equals(rrt.getQualifiedName())) {
                    continue;
                }
                if (rrt.getTypeDeclaration().get() instanceof JavaParserClassDeclaration) {
                    JavaParserClassDeclaration jpcd = (JavaParserClassDeclaration) rrt.getTypeDeclaration().get();
                    ResolvedClassDeclaration rcd = jpcd.asClass();
                    if (Object.class.getName().equals(rcd.getClassName())) {
                        continue;
                    }
                    fqn = rcd.getPackageName() + "." + rcd.getName();
                    if (!visited.contains(fqn)) { // should fqn be fqnifyed?
                        additionalClasses.add(dir + ":" + fqn); // add to additionalClasses
                    }
                    fqn = fqnifyClass(fqn, isInnerClass(rcd));
                    String superClassName = rcd.getName();
                    String superClassVariableName = Character.toString(Character.toLowerCase(superClassName.charAt(0)))
                            .concat(superClassName.substring(1)) + "___super";
                    sb.append("  ")
                            .append(fqn)
                            .append(" ")
                            .append(superClassVariableName)
                            .append(" = ")
                            .append(counter++)
                            .append(";" + LS);
                    break;

                }
            }
            sb.append("}" + LS);
        }
    }

    private static String getPackageName(ClassOrInterfaceDeclaration clazz) {
        String fqn = clazz.getFullyQualifiedName().orElse(null);
        if (fqn == null) {
            return null;
        }
        int index = fqn.lastIndexOf(".");
        return fqn.substring(0, index);
    }

    /****************************************************************************/
    /******************************
     * utility methods *****************************
     * /
     *****************************************************************************/
    private String getEntityParameter(MethodDeclaration md, String httpMethod) {
        if (HttpServletRequestImpl.LOCATOR.equals(httpMethod)) {
            return "google.protobuf.Any";
        }
        for (Parameter p : md.getParameters()) {
            if (isEntity(p)) {
                String rawType = p.getTypeAsString();
                if (PRIMITIVE_WRAPPER_TYPES.containsKey(rawType)) {
                    return PRIMITIVE_WRAPPER_TYPES.get(rawType);
                }
                // array?
                ResolvedType rt = p.getType().resolve();
                resolvedTypes.add(rt.asReferenceType().getTypeDeclaration().get());
                String type = rt.describe();
                return fqnifyClass(type, isInnerClass(rt.asReferenceType().getTypeDeclaration().get()));
            }
        }
        needEmpty = true;
        return "gEmpty";
    }

    private static boolean isEntity(Parameter p) {
        for (AnnotationExpr ae : p.getAnnotations()) {
            if (ANNOTATIONS.contains(ae.getNameAsString())) {
                return false;
            }
        }
        String name = p.getTypeAsString();
        if (AsyncResponse.class.getName().equals(name) || AsyncResponse.class.getSimpleName().equals(name)) {
            return false;
        }
        return true;
    }

    private String getReturnType(MethodDeclaration md, String httpMethod) {
        if (isSuspended(md) || HttpServletRequestImpl.LOCATOR.equals(httpMethod)) {
            return "google.protobuf.Any";
        }
        if (isSSE(md)) {
            return SSE_EVENT_CLASSNAME;
        }
        for (Node node : md.getChildNodes()) {
            if (node instanceof Type) {
                if (node instanceof VoidType) {
                    return "google.protobuf.Any"; // ??
                }
                String rawType = ((Type) node).asString();
                int open = rawType.indexOf("<");
                int close = rawType.indexOf(">");
                if (open >= 0 && close > open) {
                    String type = rawType.substring(0, open);
                    String parameterType = rawType.substring(open + 1, close);
                    if (CompletionStage.class.getCanonicalName().contentEquals(type)
                            || CompletionStage.class.getSimpleName().contentEquals(type)) {
                        rawType = parameterType;
                    } else {
                        rawType = type;
                    }
                }
                if (PRIMITIVE_WRAPPER_TYPES.containsKey(rawType)) {
                    return PRIMITIVE_WRAPPER_TYPES.get(rawType);
                }
                if ("jakarta.ws.rs.core.Response".equals(rawType) || "Response".equals(rawType)) {
                    return "google.protobuf.Any";
                }
                // array?
                ResolvedType rt = ((Type) node).resolve();
                resolvedTypes.add(rt.asReferenceType().getTypeDeclaration().get());
                String type = ((Type) node).resolve().describe();
                return fqnifyClass(type, isInnerClass(rt.asReferenceType().getTypeDeclaration().get()));
            }
        }
        needEmpty = true;
        return "gEmpty";
    }

    private static boolean isSuspended(MethodDeclaration md) {
        for (Parameter p : md.getParameters()) {
            for (AnnotationExpr ae : p.getAnnotations()) {
                if ("Suspended".equals(ae.getNameAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCompletionStage(MethodDeclaration md) {
        for (Node node : md.getChildNodes()) {
            if (node instanceof Type) {
                String rawType = ((Type) node).asString();
                int open = rawType.indexOf("<");
                int close = rawType.indexOf(">");
                if (open >= 0 && close > open) {
                    String type = rawType.substring(0, open);
                    if (CompletionStage.class.getCanonicalName().contentEquals(type)
                            || CompletionStage.class.getSimpleName().contentEquals(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSSE(MethodDeclaration md) {
        Optional<AnnotationExpr> opt = md.getAnnotationByName("Produces");
        if (opt.isEmpty()) {
            return false;
        }
        AnnotationExpr ae = opt.get();
        List<StringLiteralExpr> list1 = ae.findAll(StringLiteralExpr.class);
        for (StringLiteralExpr sle : list1) {
            if (MediaType.SERVER_SENT_EVENTS.equals(sle.getValue())) {
                isSSE = true;
                return true;
            }
        }
        List<FieldAccessExpr> list2 = ae.findAll(FieldAccessExpr.class);
        for (FieldAccessExpr fae : list2) {
            List<Node> list3 = fae.getChildNodes();
            if (list3.size() >= 2 && list3.get(0) instanceof NameExpr && list3.get(1) instanceof SimpleName) {
                NameExpr ne = (NameExpr) list3.get(0);
                SimpleName sn = (SimpleName) list3.get(1);
                if ("MediaType".equals(ne.getName().asString()) && "SERVER_SENT_EVENTS".equals(sn.asString())) {
                    isSSE = true;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isResourceOrLocatorMethod(MethodDeclaration md) {
        for (AnnotationExpr ae : md.getAnnotations()) {
            if (HTTP_VERBS.contains(ae.getNameAsString().toUpperCase()) || "Path".equals(ae.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String removeTypeVariables(String classType) {
        int left = classType.indexOf('<');
        if (left < 0) {
            return classType;
        }
        return classType.substring(0, left);
    }

    private static boolean isInnerClass(ResolvedReferenceTypeDeclaration clazz) {
        try {
            Optional<?> opt = clazz.containerType();
            if (opt.isEmpty()) {
                return false;
            }
            ResolvedTypeDeclaration rtd = clazz.containerType().get();
            return rtd.isClass();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isInnerClass(ClassOrInterfaceDeclaration clazz) {
        return clazz.isNestedType();
    }

    private static String fqnifyClass(String s, boolean isInnerClass) {
        int l = s.lastIndexOf(".");
        String sPackage = s.substring(0, l).replace(".", "_");
        String separator = isInnerClass ? "_INNER_" : "___";
        String className = s.substring(l + 1);
        return sPackage + separator + className;
    }

    private static void fqnifyClass(final String s, final boolean isInnerClass, final StringBuilder sb) {
        int l = s.lastIndexOf(".");
        sb.append(s.substring(0, l).replace('.', '_'))
                .append(isInnerClass ? "_INNER_" : "___")
                .append(s.substring(l + 1));
    }

    private static String namify(String s) {
        return s.replace(".", "_");
    }

    private static String getHttpMethod(MethodDeclaration md) {
        if (md.getAnnotationByName("DELETE").isPresent()) {
            return "DELETE";
        }
        if (md.getAnnotationByName("GET").isPresent()) {
            return "GET";
        }
        if (md.getAnnotationByName("HEAD").isPresent()) {
            return "HEAD";
        }
        if (md.getAnnotationByName("OPTIONS").isPresent()) {
            return "OPTIONS";
        }
        if (md.getAnnotationByName("PATCH").isPresent()) {
            return "PATCH";
        }
        if (md.getAnnotationByName("POST").isPresent()) {
            return "POST";
        }
        if (md.getAnnotationByName("PUT").isPresent()) {
            return "PUT";
        }
        return HttpServletRequestImpl.LOCATOR;
    }
}
