import groovy.json.StringEscapeUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

class PietAssembler {
    private final static Pattern IsIntegerPattern = Pattern.compile("^\\d+$");

    private static boolean isInteger(String str) {
        if(str.length() == 0) return false;
        if(str.charAt(0) == '-') {
            if(str.length() == 1) return false;
            if(str.charAt(1) == '-') return false;
            return isInteger(str.substring(1));
        }
        return IsIntegerPattern.matcher(str).matches();
    }

//    private static List<Integer> convertToCodePoints(String s){
//        List<Integer> sequence = new ArrayList<>(s.length());
//        for(int i = s.length()-1; i >= 0; i--){
//            sequence.add(s.codePointAt(i));
//        }
//        return sequence;
//    }
//
//    private static void test(){
//        List<Integer> sequence = convertToCodePoints("Hello World!\n\tHow are you doing today?\nI'm being forced to generate sequences of numbers that represent strings using only the integers 1, 2, 3, and 4!");
//        sequence.add(sequence.size());
//        System.out.println(sequence.size() + ", " + sequence);
////        for(int i = 100; i > 0; i--) {
////            pushSequence(sequence);
////            newPushSequence(sequence);
////        }
//
//        List<String> old = new ArrayList<>();
//        long start = System.currentTimeMillis();
////        for(int i = 100; i > 0; i --) {
//            old = pushSequence(sequence);
////        }
//        long oldTime = System.currentTimeMillis() - start;
//
//        List<String> n3w = new ArrayList<>();
//        start = System.currentTimeMillis();
////        for(int i = 100; i > 0; i --) {
//            n3w = newPushSequence(sequence);
////        }
//        long newTime = System.currentTimeMillis() - start;
//
//        System.out.println("Old: " + oldTime + ", " + old.size() + ", " + (float)(old.size())/sequence.size() + ", " + old);
//        System.out.println("New: " + newTime + ", " + n3w.size() + ", " + (float)(n3w.size())/sequence.size() + ", " + n3w);
//
//        List<RawFunctionContainer> functions = List.of(
//                new RawFunctionContainer("main", "old;print;otC \\n;new;print;otC \\n;end;"),
//                new RawFunctionContainer("print", "rol 3 -1;dup;rol 4 1;add 3;psh 2;rol;:output;rol 2 1;otC;sub 1;dup;jump not 0 output;pop;"),
//                new RawFunctionContainer("old", String.join(";", old) + ";psh 2;psh 1;"),
//                new RawFunctionContainer("new", String.join(";", n3w) + ";psh 4;psh 1;")
//        );
//        List<List<String>> commands = parseCode(functions);
//        createImage(commands, "/Users/wk48343/Desktop/Java Programs/Piet Compiler/src/output.pbm");
//
//        throw new TerminateProgram();
//    }

    public static void main(String[] args) {
        try {
            System.out.println("Running");

            List<RawFunction> functions = getFunctions("/Users/wk48343/Desktop/Java Programs/Piet Assembler/src/text adventure/", "main.txt");
//            List<RawFunction> functions = getFunctions("/Users/wk48343/Desktop/Java Programs/Piet Assembler/src/", "code.txt");
            if(functions.size() == 0){
                System.out.println("No functions found in file. Ending.");
                throw new TerminateProgram();
            }

            System.out.println();
            System.out.println(functions);

            List<List<String>> commands = parseCode(functions);

//            System.out.println();
//            System.out.println(commands);

            createImage(commands, "/Users/wk48343/Desktop/Java Programs/Piet Assembler/src/output.pbm");
        } catch (TerminateProgram ignored){
        }
    }

    private static final List<String> includedInput = new ArrayList<>();

    private static List<RawFunction> getFunctions(String directory, String fileName) {
        String input;
        try {
            input = Files.readString(Path.of(directory + fileName), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            System.out.println("File input for \"" + directory + fileName + "\" failed:");
            e.printStackTrace();
            throw new TerminateProgram();
        }
        if (includedInput.contains(input)) {
            throw new TerminateProgram();
        }
        includedInput.add(input);
        input = input.replace("\n", "").replace("    ", "");

        String namePrefix = fileName.substring(0, fileName.length()-3);

        System.out.println(input);

        List<RawFunction> functions = new ArrayList<>();
        List<RawFunction> includedFunctions = new ArrayList<>();

        // parser variables
        int mode = 0;
        boolean inComment = false;

        // building variables
        StringBuilder nameBuild = new StringBuilder();
        StringBuilder codeBuild = new StringBuilder(";");

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inComment) {
                if (c == ';') { // has comment ended?
                    inComment = false;
                }
            } else if (c == '#') { // comment started
                inComment = true;
            } else {
                switch (mode) {
                    case 0: // looking for def or include statement;
                        if (c == '{') {
                            // start of code block
                            mode = 1; // start reading code
                        } else if (c == ';') {
                            // end of include statement
                            try {
                                String statement = nameBuild.toString();
                                if (!statement.startsWith("include")) {
                                    // yellow text
                                    System.out.println("\u001B[33mWarning: found stray staement: \"" + statement + "\" outside of function. Dosen't seem to be an include. Ignoring this statement.\u001B[0m");
                                    throw new JumpOut();
                                }
                                if (statement.length() < 9 || statement.charAt(7) != ' ') {
                                    // yellow text
                                    System.out.println("\u001B[33mWarning: statement: \"" + statement + "\" appears to be an invalid include phrase with nothing after it or directly appended. Ignoring this statement.\u001B[0m");
                                    throw new JumpOut();
                                }
                                String includeFilePath = statement.split(" ", 2)[1];
                                if (!includeFilePath.endsWith(".txt")) {
                                    // yellow text
                                    System.out.println("\u001B[33mWarning: filename: \"" + includeFilePath + "\" from include: \"" + statement + "\" dose not end in '.txt'. Ignoring this statement.\u001B[0m");
                                    throw new JumpOut();
                                }
                                String[] fileSplit = includeFilePath.split("/");
                                String newFileName = fileSplit[fileSplit.length - 1];
                                String addedPath = includeFilePath.substring(0, includeFilePath.length() - newFileName.length());

                                try {
                                    includedFunctions.addAll(getFunctions(directory + addedPath, newFileName));
                                } catch (TerminateProgram ignored) {
                                    System.out.println("Skipping include");
                                    throw new JumpOut();
                                }
                                System.out.println("Including functions from " + directory + addedPath + newFileName);
                            } catch (JumpOut ignored) {
                            }
                            nameBuild = new StringBuilder();
                        } else {
                            nameBuild.append(c);
                        }
                        break;
                    case 1: // reading code
                        if (c == '}') {
                            functions.add(new RawFunction(nameBuild.toString(), codeBuild.toString()));

                            // reset variables
                            nameBuild = new StringBuilder();
                            codeBuild = new StringBuilder(";");

                            mode = 0; // start looking for next function
                        } else {
                            codeBuild.append(c);
                        }
                        break;
                }
            }
        }

        List<String> namesToPrefix = new ArrayList<>(functions.size());
        for(RawFunction function: functions){
            namesToPrefix.add(function.name());
        }
        List<RawFunction> prefixFunctions = new ArrayList<>(functions.size());
        for(RawFunction function: functions){
            prefixFunctions.add(function.prefix(namePrefix, namesToPrefix));
        }

        prefixFunctions.addAll(includedFunctions);
        return prefixFunctions;
    }

    private static List<List<String>> parseCode(List<RawFunction> functions) {
        ArrayList<String> functionNames = new ArrayList<>();
        for(RawFunction function: functions) functionNames.add(function.name());

        System.out.println();
        boolean parsedSuccessfully = true;
        List<List<String>> allCommands = new ArrayList<>();
        for(RawFunction function: functions){
            String rawCode = function.code();
            String[] commands = rawCode.split(";");

            List<String> expandedCommands = new ArrayList<>();
            GlobalValue<Integer> nextJumpPointId = new GlobalValue<>(2); // entry 1 point is start, 2 is available
            GlobalValue<Boolean> inUnreachableCode = new GlobalValue<>(false);
            HashMap<String, Integer> jumpPointNameMap = new HashMap<>();
            for(int i = 0; i < commands.length; i++){
                String command = commands[i];
                if(command.startsWith(":")){
                    inUnreachableCode.value = false;
                }
                if(!inUnreachableCode.value) {
                    String nextCommand = i == commands.length - 1 ? "" : commands[i + 1];
                    try {
                        expandedCommands.addAll(expandCommand(command, function, functionNames, jumpPointNameMap,
                                nextJumpPointId, nextCommand, inUnreachableCode));
                    } catch (CommandFormatError e) {
                        // color red with ANSI color codes
                        System.out.println("\u001B[31m" + e.getMessage() + "\u001B[0m");
                        parsedSuccessfully = false; // don't end program now. If other problems exist, they should still be reported.
                    }
                } else {
                    // color yellow with ANSI color codes
                    System.out.println("\u001B[33mWarning! Command \"" + command + "\" in " +  function.name()+ " is unreachable\u001B[0m");
                }
            }

            // replace jumps forward with proper psh commands now that all jumps have been identified
            for(int i=expandedCommands.size()-1; i >= 0; i--){
                String command = expandedCommands.get(i);
                if(command.startsWith(":") && command.length() > 1){
                    String name = command.substring(1);
                    if(!jumpPointNameMap.containsKey(name)){
                        CommandFormatError e = new CommandFormatError(function, "jump " + command, "jump point \"" + name + "\" not found or recognized");
                        System.out.println(e.getMessage());
                        parsedSuccessfully = false;
                    } else {
                        int id = jumpPointNameMap.get(name);
                        expandedCommands.remove(i);
                        expandedCommands.addAll(i, pushNumber(id));
                    }
                }
            }

            System.out.println(expandedCommands);
            allCommands.add(expandedCommands);
        }
        System.out.println();

        // remove functions that aren't used in a call to first method
        Map<String, List<String>> usedFunctionsGraph = new HashMap<>();
        for(String name: functionNames) usedFunctionsGraph.put(name, new ArrayList<>());
        for(String name: functionNames){
            List<String> commands = allCommands.get(functionNames.indexOf(name));
            for(String command: commands){
                if(command.startsWith("func: ")){
                    String function = command.substring(6);
                    usedFunctionsGraph.get(name).add(function);
                }
            }
        }
        List<String> functionsUsedByMain = new ArrayList<>();
        try {
            findUsedFunctions(functionNames.get(0), functionsUsedByMain, usedFunctionsGraph);
        } catch(JumpOut ignored){
            System.out.println("Code parse failed");
            throw new TerminateProgram();
        }
        for(int i = functionNames.size()-1; i >= 0; i--){
            String name = functionNames.get(i);
            if(!functionsUsedByMain.contains(name)){
                System.out.println(name + " not used, removing");
                functionNames.remove(i);
                allCommands.remove(i);
            }
        }
        System.out.println("Used functions: " + functionNames);
        System.out.println();

        // replace function calls now that necessary functions have been identified and ordered
        for(List<String> commands:allCommands){
            for(int i=commands.size()-1; i >= 0; i--){
                String command = commands.get(i);
                if(command.startsWith("func: ")){
                    String function = command.substring(6);
                    int id = functionNames.indexOf(function) + 1;
                    commands.remove(i);
                    commands.addAll(i, pushNumber(id));
                }
            }
        }

        if(parsedSuccessfully) {
            System.out.println("Code parse successful");
            return allCommands;
        }
        System.out.println("Code parse failed");
        throw new TerminateProgram();
    }

    private static List<String> expandCommand(String command, RawFunction function,
                                              ArrayList<String> functionNames, HashMap<String, Integer> jumpPointNameMap,
                                              GlobalValue<Integer> nextJumpPointId, String nextCommand,
                                              GlobalValue<Boolean> inUnreachableCode) throws CommandFormatError {
        if(command.length() == 0) return Collections.emptyList();
        List<String> commands = new ArrayList<>();

        // check if command meets format of the basic Piet commands
        boolean basicFound = false;
        int length = command.length();
        try {
            if (length >= 3) {
                String commandCode = command.substring(0, 3);
                String[] args = new String[0];

                if (length >= 4) {
                    if (command.charAt(3) != ' ' && !command.startsWith("pshOrder")) throw new JumpOut();
                    args = command.split(" ");
                    args = Arrays.copyOfRange(args, 1, args.length);
                }

                basicFound = true;
                switch (commandCode) {
                    case "psh" -> {
                        if (args.length == 0)
                            throw new CommandFormatError(function, command, "psh command must provide value(s) to push.");

                        boolean addLength = args[0].equals("\\length");
                        if(addLength) args = Arrays.copyOfRange(args, 1, args.length);

                        if (args.length == 0)
                            throw new CommandFormatError(function, command, "psh command must provide value(s) to push.");
                        boolean reverse = command.startsWith("pshOrder");
                        if(reverse) Collections.reverse(Arrays.asList(args));

                        List<Integer> values = new ArrayList<>();
                        for (String valueString : args) {
                            if (isInteger(valueString)) {
                                values.add(Integer.parseInt(valueString));
                            } else {
                                valueString = StringEscapeUtils.unescapeJava(valueString);

                                if (valueString.length() == 0) valueString = " ";
                                if(reverse) valueString = (new StringBuilder(valueString).reverse().toString());

                                for (int i = 0; i < valueString.length(); i++) {
                                    values.add(valueString.codePointAt(i));
                                }
                            }
                        }
                        if(addLength) values.add(values.size());
                        commands.addAll(pushSequence(values));
                    }
                    case "pop" -> {
                        if (args.length == 0) {
                            commands.add("pop");
                        } else if (args.length == 1) {
                            if (!isInteger(args[0]))
                                throw new CommandFormatError(function, command, "pop command must have integer argument");
                            int repeat = Integer.parseInt(args[0]);
                            commands.addAll(Collections.nCopies(repeat, "pop"));
                        } else {
                            throw new CommandFormatError(function, command, "pop command can not have more than 1 argument");
                        }
                    }
                    case "otC" -> {
                        if (args.length == 0) {
                            commands.add("otC");
                        } else {
                            for (String string : args) {
                                string = StringEscapeUtils.unescapeJava(string);
                                if (string.length() == 0) {
                                    string = " ";
                                }
                                for (int i = 0; i < string.length(); i++) {
                                    commands.addAll(pushNumber(string.codePointAt(i)));
                                    commands.add("otC");
                                }
                            }
                        }
                    }
                    case "otN" -> {
                        if (args.length == 0) {
                            commands.add("otN");
                        } else {
                            for (String string : args) {
                                if (!isInteger(string))
                                    throw new CommandFormatError(function, command, "otN command should only have integers as its arguments");
                                commands.addAll(pushNumber(Integer.parseInt(string)));
                                commands.add("otN");
                            }
                        }
                    }
                    case "rol" -> {
                        if (args.length == 0) {
                            commands.add("rol");
                        } else if (args.length == 2) {
                            if (!(isInteger(args[0]) && isInteger(args[1])))
                                throw new CommandFormatError(function, command, "rol command should only have integers as its arguments");
                            int area = Integer.parseInt(args[0]);
                            int rolls = Integer.parseInt(args[1]);
                            rolls = rolls % area;
                            if(rolls != 0) {
                                // rolls and (rolls-area)%area will give the same result
                                // check which number is more efficient to push to the stack
                                List<String> optimalRolls;
                                List<String> positiveRolls;
                                List<String> negativeRolls;
                                if(rolls > 0){
                                    positiveRolls = pushNumber(rolls);
                                    negativeRolls = pushNumber(rolls - area);
                                } else {
                                    positiveRolls = pushNumber(rolls + area);
                                    negativeRolls = pushNumber(rolls);
                                }
                                // if equal, go positive for readability
                                if(positiveRolls.size() <= negativeRolls.size()){
                                    optimalRolls = positiveRolls;
                                } else {
                                    optimalRolls = negativeRolls;
                                }

                                commands.addAll(pushNumber(area));
                                commands.addAll(optimalRolls);
                                commands.add("rol");
                            } // if rolls == 0 command will do nothing, so don't bother adding anything
                        } else {
                            throw new CommandFormatError(function, command, "Wrong number of arguments for rol");
                        }
                    }
                    case "add", "sub", "mul", "div", "mod", "grt" -> {
                        if (args.length == 0) {
                            commands.add(commandCode);
                        } else if (args.length == 1) {
                            if (!isInteger(args[0]))
                                throw new CommandFormatError(function, command, commandCode + " command must have integer argument");
                            int arg = Integer.parseInt(args[0]);
                            commands.addAll(pushNumber(arg));
                            commands.add(commandCode);
                        } else {
                            throw new CommandFormatError(function, command, commandCode + " command can not have more than 1 argument");
                        }
                    } // none or one integer argument
                    case "dup", "not", "inC", "inN" -> {
                        if (args.length != 0)
                            throw new CommandFormatError(function, command, commandCode + " command can not have any arguments");
                        commands.add(commandCode);
                    } // no arguments
                    default -> basicFound = false;
                }
            }
        } catch (JumpOut ignored){}

        if(!basicFound) {
            if (command.equals("end")) {
                // equivalent to calling function with 0 call address
                commands.addAll(pushNumber(0));
                commands.add("dup"); // gets past switch in checks to escape through input line

                if (!nextCommand.equals("")) {
                    // If no next command, switch check isn't inserted
                    commands.add("psh 1"); // function call will run into switch check, this makes it do nothing
                }
                commands.add("psh 3");
                commands.add("pnt"); // rotates counterclockwise to escape
                inUnreachableCode.value = true;
            }
            else if (command.equals("return")) {
                // jump to function with the assumption that the target is already at top of stack
                commands.addAll(pushNumber(0)); // gets past switch in checks to escape through input line

                if (!nextCommand.equals("")) {
                    // If no next command, switch check isn't inserted
                    commands.add("psh 1"); // function call will run into switch check, this makes it do nothing
                }
                commands.add("psh 3");
                commands.add("pnt"); // rotates counterclockwise to escape
                inUnreachableCode.value = true;
            }
            // function calls, jump points and instructions, invalid commands
            else if (functionNames.contains(command)) {
                // function call
                int returnPoint = nextJumpPointId.value;
                if(!nextCommand.startsWith(":"))// If next command declares entry point let it use same index
                    nextJumpPointId.value++;
                int callPoint = 1;

                commands.addAll(pushNumber(returnPoint));
                commands.add("func: " + function.name()); // ask outside once all functions are ordered
                commands.addAll(pushNumber(callPoint));
                commands.add("func: " + command);

                commands.addAll(pushNumber(0)); // gets past switch in checks to escape through input line
                if (!nextCommand.equals("")) {
                    // If no next command, switch check isn't inserted
                    commands.add("psh 1"); // function call will run into switch check, this makes it do nothing
                }
                commands.add("psh 3");
                commands.add("pnt"); // rotates counterclockwise to escape
                if(!nextCommand.startsWith(":"))
                    commands.add(":"); // add branch entrance ahead unless next command adds it anyways
            }
            else if (command.charAt(0) == ':') {
                // entry point definition
                String name = command.substring(1);
                if(jumpPointNameMap.containsKey(name)) throw new CommandFormatError(function, command, "Jump point \"" + name + "\" already used");

                int id = nextJumpPointId.value;
                nextJumpPointId.value++;

                jumpPointNameMap.put(name, id);

                commands.add(":");
            }
            else if (command.startsWith("jump")) {
                if (command.length() <= 4 || command.charAt(4) != ' ')
                    throw new CommandFormatError(function, command, "jump command must be followed by arguments.");

                String[] args = command.substring(5).split(" ");

                if (args.length == 0)
                    throw new CommandFormatError(function, command, "jump command must be followed by arguments.");

                String target = null;
                boolean conditional = false;
                List<String> pntValueCommands = new ArrayList<>();

                switch (args[0]) {
                    case "not":
                        if (args.length <= 2 || !isInteger(args[1])) {
                            // invalid not, maybe jump point
                            target = String.join(" ", args);
                        } else {
                            int parameter = Integer.parseInt(args[1]);
                            target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                            if(parameter != 0) {
                                pntValueCommands.addAll(pushNumber(parameter));
                                pntValueCommands.add("sub");
                            }
                            pntValueCommands.add("not");
                            pntValueCommands.add("not");
                            pntValueCommands.add("psh 3");
                            pntValueCommands.add("mul");

                            conditional = true;
                        }
                        break;
                    case "equ":
                        if (args.length <= 2 || !isInteger(args[1])) {
                            // invalid add, maybe jump point
                            target = String.join(" ", args);
                        } else {
                            int parameter = Integer.parseInt(args[1]);
                            target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                            if(parameter != 0) {
                                pntValueCommands.addAll(pushNumber(parameter));
                                pntValueCommands.add("sub");
                            }
                            pntValueCommands.add("not");
                            pntValueCommands.add("psh 3");
                            pntValueCommands.add("mul");

                            conditional = true;
                        }
                        break;
                    case "grt":
                        if (args.length <= 2 || !isInteger(args[1])) {
                            // invalid grt, maybe jump point
                            target = String.join(" ", args);
                        } else {
                            int parameter = Integer.parseInt(args[1]);
                            target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                            pntValueCommands.addAll(pushNumber(parameter));
                            pntValueCommands.add("grt");
                            pntValueCommands.add("psh 3");
                            pntValueCommands.add("mul");

                            conditional = true;
                        }
                        break;
                    case "les":
                        if (args.length <= 2 || !isInteger(args[1])) {
                            // invalid les, maybe jump point
                            target = String.join(" ", args);
                        } else {
                            int parameter = Integer.parseInt(args[1]) - 1;
                            target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                            pntValueCommands.addAll(pushNumber(parameter));
                            pntValueCommands.add("grt");
                            pntValueCommands.add("not");
                            pntValueCommands.add("psh 3");
                            pntValueCommands.add("mul");

                            conditional = true;
                        }
                        break;
                }

                if (!conditional) {
                    pntValueCommands.add("psh 3");
                    target = String.join(" ", args);
                }

                if(target.startsWith("func: ")){
                    // option to jump to function without adding return address
                    commands.add("psh 1");
                    commands.add(target);
                }
                else {
                    if (jumpPointNameMap.containsKey(target)) {
                        int jumpId = jumpPointNameMap.get(target);
                        commands.addAll(pushNumber(jumpId));
                    } else {
                        commands.add(":" + target); // if jump point not found, ask outside function once all points considered
                    }
                    commands.add("func: " + function.name()); // ask outside code for final function id
                }
                commands.addAll(pushNumber(0)); // skips switch in checks
                boolean nextJumpDeclaration = nextCommand.startsWith(":");
                if (nextJumpDeclaration && !conditional) {
                    // exit will run into switch in check
                    commands.add("psh 1");
                }
                if (conditional) {
                    // need to get parameter from under jump information
                    commands.add("psh 4");
                    commands.add("psh 3");
                    commands.add("rol");
                }
                commands.addAll(pntValueCommands);
                if (!nextJumpDeclaration || conditional) {
                    // exit will switch once when running into wall
                    commands.add("psh 1");
                    commands.add("swt");
                }
                commands.add("pnt");

                if (conditional) {
                    // when condition fails, need to pop jump target stuff off stack
                    commands.add("pop");
                    commands.add("pop");
                    commands.add("pop");
                    if(nextJumpDeclaration){
                        commands.add("wht");
                    }
                }
                inUnreachableCode.value = !conditional;
            }
            else {
                throw new CommandFormatError(function, command, "command not recognized.");
            }
        }
        return commands;
    }

    private static void findUsedFunctions(String node, List<String> used, Map<String, List<String>> usedFunctionsGraph) throws JumpOut {
        used.add(node);
        List<String> toAdd = usedFunctionsGraph.get(node);
        if(toAdd == null){
            System.out.println("Error: Could not find function " + node + ". Make sure it's file is included in program dependencies");
            throw new JumpOut();
        }
        for(String name:toAdd){
            if(!used.contains(name)){
                try {
                    findUsedFunctions(name, used, usedFunctionsGraph);
                } catch (JumpOut ignored){
                    System.out.println("\tCalled by " + node);
                    throw new JumpOut();
                }
            }
        }
    }

    private static List<String> pushNumber(int value){
        List<String> commands;
        if(value == 0){
            // 0 is the result of not on any number, less than psh 1 psh 1 sub
            commands = new ArrayList<>();
            commands.add("psh 1");
            commands.add("not");
        }
        else if(value < 1){
            // negative numbers are 1 - (-value + 1)
            commands = new ArrayList<>();
            commands.add("psh 1");
            commands.addAll(pushNumber(-1 * value + 1));
            commands.add("sub");
        } else {
            commands = pushNumber(value, Integer.MAX_VALUE);
        }
        return commands;
    }

    private static List<String> pushNumber(int value, int maxCommandLength){
        if(maxCommandLength <= 0)
            return new ArrayList<>();
        if(value <= 0) return pushNumber(value);

        int maxPshLength = 4;
        List<String> commands = new ArrayList<>();

        if(value <= maxPshLength){
            commands.add("psh " + value);
        } else {
            int best_length = Integer.MAX_VALUE;
            for (int remainder = 0; remainder < maxPshLength; remainder++) {
                for (int divisor = maxPshLength; divisor >= 2; divisor--) {
                    if (remainder < divisor) {
                        if (value % divisor == remainder) {
                            List<String> testCommands;
                            if(value / divisor != 1) {
                                testCommands = pushNumber(value / divisor,
                                        maxCommandLength - 2 - (remainder != 0 ? 2:0));
                                testCommands.add("psh " + divisor);
                                testCommands.add("mul");
                            } else {
                                testCommands = new ArrayList<>();
                                testCommands.add("psh " + divisor);
                            }
                            if(remainder != 0) {
                                testCommands.add("psh " + remainder);
                                testCommands.add("add");
                            }

                            if (testCommands.size() < best_length) {
                                commands = testCommands;
                                best_length = testCommands.size();
                                maxCommandLength = best_length;
                            }

                            if(remainder != 0) {
                                testCommands = pushNumber(value / divisor + 1, maxCommandLength-4);
                                testCommands.add("psh " + divisor);
                                testCommands.add("mul");
                                testCommands.add("psh " + (divisor - remainder));
                                testCommands.add("sub");

                                if (testCommands.size() < best_length) {
                                    commands = testCommands;
                                    best_length = testCommands.size();
                                    maxCommandLength = best_length;
                                }
                            }
                        }
                    }
                }
            }
        }

        return commands;
    }

    private static List<String> pushSequence(List<Integer> values){
        List<String> commands = new ArrayList<>(pushNumber(values.get(0)));

        for(int i = 1; i < values.size(); i++){
            int old = values.get(i-1);
            int now = values.get(i);

            if(old == now){
                commands.add("dup");
            } else {
                boolean subtracting = old > now;
                List<String> addOn = new ArrayList<>(Collections.singleton("dup"));
                addOn.addAll(pushNumber(Math.abs(now - old)));
                addOn.add(subtracting ? "sub":"add");

                List<String> create = pushNumber(now, addOn.size());

                if(create.size() <= addOn.size()){
                    commands.addAll(create);
                } else {
                    commands.addAll(addOn);
                }
            }
        }

        return commands;
    }

//    private static List<String> newPushSequence(List<Integer> values){
//        List<String> commands = new ArrayList<>(pushNumber(values.get(0)));
//        List<Integer> accessibleInts = new ArrayList<>();
//        accessibleInts.add(values.get(0));
//
//        for(int i = 1; i < values.size(); i++) {
//            int now = values.get(i);
//            // push number directly
//            List<String> best = pushNumber(now);
//            for (int j = 0; j < accessibleInts.size(); j++) {
//                // add/subtract from a value already in stack
//                int old = accessibleInts.get(j);
//                int depth = accessibleInts.size() - j;
//
//                List<String> addOn = new ArrayList<>();
//                if (depth != 1) {
//                    // roll number up
//                    addOn.addAll(pushNumber(depth));
//                    addOn.addAll(pushNumber(-1));
//                    addOn.add("rol");
//                    addOn.add("dup");
//                    addOn.addAll(pushNumber(depth + 1));
//                    addOn.add("psh 1");
//                    addOn.add("rol");
//                } else {
//                    addOn.add("dup");
//                }
//
//                if (old != now)  {
//                    boolean subtracting = old > now;
//                    addOn.addAll(pushNumber(Math.abs(now - old)));
//                    addOn.add(subtracting ? "sub" : "add");
//                }
//
//                if(addOn.size() < best.size()){
//                    best = addOn;
//                }
//            }
//            commands.addAll(best);
//            accessibleInts.add(now);
//        }
//
//        return commands;
//    }

    private static void createImage(List<List<String>> functions, String filepath){
        // 3 left to start, 7 for each function, 4 to right
        int width = 3 + 7 * functions.size() + 4;
        // height calculated while splitting code
        int height = 0;

        List<List<List<String>>> splitFunctions = new ArrayList<>();
        for(List<String> code:functions){
            List<List<String>> splitCode = new ArrayList<>();
            List<String> currentSection = new ArrayList<>();
            int localHeight = 8;
            for(String command:code){
                if(command.equals(":")){
                    if(currentSection.size() < 3){
                        //too short to fit switch-in block
                        localHeight += 7;
                        currentSection.addAll(Collections.nCopies(3-currentSection.size(), "wht"));
                    } else {
                        localHeight += currentSection.size() + 4;
                    }

                    splitCode.add(currentSection);
                    currentSection = new ArrayList<>();
                } else {
                    currentSection.add(command);
                }
            }
            if(currentSection.size() != 0){
                // no switch in block needed for last
                localHeight += currentSection.size() + 4;

                splitCode.add(currentSection);
            }

            if(localHeight > height){
                height = localHeight;
            }

            splitFunctions.add(splitCode);
        }
        height++; // return row

        String header = "P6" + "\n" +
                width + " " +
                height + "\n" +
                "255" + "\n";

        // start building image

        // initialize all Black
        Color[][] colors = new Color[height][width];
        for(Color[] row: colors){
            Arrays.fill(row, Color.BLACK);
        }

        // initialize stack with call to first function
        applyCommands(colors, 0, 0, 1, 0, "str", "psh", "psh");
        colors[1][1] = colors[0][1];
        // inverted commands that end up adding 1 to function index on return line
        // psh, add
        applyCommands(colors, 2, 1, 0, 1, "inC", "pop");

        // insert vertical return line
        for(int y=3; y < height; y++){
            colors[y][2] = Color.WHITE;
        }

        // insert horizontal return line
        for(int x=2; x<width - 4; x++){
            colors[height-1][x] = Color.WHITE;
        }

        // insert CC fixer
        colors[0][4] = Color.START;
        colors[1][4] = Color.START;

        // insert stop block
        applyCommands(colors, width - 4, 0, 1, 0, "wht", "str", "pop");
        applyCommands(colors, width - 2, 1, 0, 1, "nop", "nop");
        colors[2][width - 1] = colors[2][width - 2];
        colors[2][width - 3] = colors[2][width - 2];

        // insert functions
        int xOffset = 3;
        for(List<List<String>> function: splitFunctions) {
            //insert pnt choice
            applyCommands(colors, xOffset, 0, 1, 0,
                    "wht", "str", "psh", "sub", "dup", "not", "pnt");
            applyCommands(colors, xOffset + 6, 1, 0, 1,
                    "pop");


            int yOffset = 8;
            int previousCodeSize = 0;
            for (List<String> code : function) {
                // insert switch choice
                if(yOffset == 8)
                    applyCommands(colors, xOffset + 6, 2, 0, 1,
                            "psh", "sub", "dup", "not", "swt");
                else
                    applyCommands(colors, xOffset + 6, yOffset - 8, 0, 1,
                            "wht", "str", "psh", "sub", "dup", "not", "swt");
                colors[yOffset - 2][xOffset + 5] = colors[yOffset - 2][xOffset + 6];
                applyCommands(colors, xOffset + 5, yOffset - 1, 0, 1, "pop");
                colors[yOffset - 1][xOffset + 4] = colors[yOffset - 1][xOffset + 5];
                if (yOffset != 8) {
                    // not first switch in, need to connect to previous code line
                    colors[yOffset - 2][xOffset + 4] = Color.WHITE;
                    // connect to previous switch choice
                    applyCommands(colors, xOffset + 6, yOffset - 9, 0, -1,
                            Collections.nCopies(previousCodeSize - 3, "wht").toArray(new String[0]));
                }

                // add commands
                applyCommands(colors, xOffset + 4, yOffset, 0, 1,
                        "wht", "str");
                applyCommands(colors, xOffset + 4, yOffset + 2, 0, 1,
                        code.toArray(new String[0]));

                // move to next code area
                yOffset += code.size() + 4;
                previousCodeSize = code.size();
            }
            // connect down to return line
            yOffset -= 2;
            // command line
            applyCommands(colors, xOffset + 4, yOffset, 0, 1,
                    Collections.nCopies(height - yOffset, "wht").toArray(new String[0]));
            // switch choice line
            applyCommands(colors, xOffset + 6, yOffset - previousCodeSize - 3, 0, 1,
                    Collections.nCopies(height - yOffset + previousCodeSize, "wht").toArray(new String[0]));
            applyCommands(colors, xOffset + 6, height - 3, 0, 1,
                    "str", "pop");

            xOffset += 7; // move to next function area
        }

        List<Byte> image = new ArrayList<>();
        for(Color[] row: colors){
            for(Color color: row){
                image.addAll(Arrays.asList(color.getRGB()));
            }
        }

        writeImage(header, image, filepath);
    }

    private static void applyCommands(Color[][] image, int x, int y, int xChange, int yChange, String... commands){
        for(String command: commands) {
            if (command.equals("str")) {
                image[y][x] = Color.START;
            } else if (command.equals("wht")) {
                image[y][x] = Color.WHITE;
            } else if (command.startsWith("psh")) {
                image[y][x] = image[y - yChange][x - xChange].applyCommand("psh");
                if (xChange == 0 && yChange == 1 && command.length() > 3) {
                    // validate assumption that push with a number only appears moving down
                    int arg = Integer.parseInt(command.substring(4));
                    if (arg != 1) // extend previous block to get to psh #
                        applyCommands(image, x - 1, y - 1, -1, 0, Collections.nCopies(arg - 1, "nop").toArray(new String[0]));
                }
            } else if (command.equals("pnt")) {
                image[y][x] = image[y - yChange][x - xChange].applyCommand("pnt");
                if (xChange == 0 && yChange == 1) {
                    image[y][x + 1] = Color.WHITE;
                }
            } else {
                image[y][x] = image[y - yChange][x - xChange].applyCommand(command);
            }
            x += xChange;
            y += yChange;
        }
    }

    private static void writeImage(String header, List<Byte> image, String filepath) {
        try (FileOutputStream out = new FileOutputStream(filepath)) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
            for(byte b:image) {
                out.write(b);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw new TerminateProgram();
        }

    }
}

record RawFunction(String name, String code) {
    public String toString() {
        return "RawFunction{" +
                "name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }

    public RawFunction prefix(String prefix, List<String> functionNames){
        String newName = prefix+name;
        String newCode = code;
        for(String function:functionNames){
            newCode = newCode.replace(";" + function + ";", ";" + prefix + function + ";");
            newCode = newCode.replace("func: " + function + ";", "func: " + prefix + function + ";");
        }
        return new RawFunction(newName, newCode);
    }
}

record Color(int hue, int shade) {
    private static final Map<String, ColorChange> commandToColorChange = Map.ofEntries(
            Map.entry("nop", new ColorChange(0, 0)),
            Map.entry("psh", new ColorChange(0, 1)),
            Map.entry("pop", new ColorChange(0, 2)),
            Map.entry("add", new ColorChange(1, 0)),
            Map.entry("sub", new ColorChange(1, 1)),
            Map.entry("mul", new ColorChange(1, 2)),
            Map.entry("div", new ColorChange(2, 0)),
            Map.entry("mod", new ColorChange(2, 1)),
            Map.entry("not", new ColorChange(2, 2)),
            Map.entry("grt", new ColorChange(3, 0)),
            Map.entry("pnt", new ColorChange(3, 1)),
            Map.entry("swt", new ColorChange(3, 2)),
            Map.entry("dup", new ColorChange(4, 0)),
            Map.entry("rol", new ColorChange(4, 1)),
            Map.entry("inN", new ColorChange(4, 2)),
            Map.entry("inC", new ColorChange(5, 0)),
            Map.entry("otN", new ColorChange(5, 1)),
            Map.entry("otC", new ColorChange(5, 2))
    );
    public static final Color BLACK = new Color(-1, 0);
    public static final Color WHITE = new Color(-1, 1);
    public static final Color START = new Color(0, 1);
    public static final Random rand = new Random();

    public static Color randomStart(){
        return new Color(rand.nextInt(6), rand.nextInt(3));
    }

    public Color applyCommand(String command) {
        ColorChange change = commandToColorChange.get(command);
        return new Color((hue + change.hue()) % 6, (shade + change.shade()) % 3);
    }

    public Byte[] getRGB() {
        // Colors contain #00, #C0, #FF
        // Converts to     0,   192, 255
        byte dark = (byte) 0;
        byte medium = (byte) 192;
        byte light = (byte) 255;

        boolean firstMain = false;
        boolean secondMain = false;
        boolean thirdMain = false;

        switch (hue) {
            case -1:
                return switch (shade) {
                    case 0 -> new Byte[]{dark, dark, dark};
                    case 1 -> new Byte[]{light, light, light};
                    default -> null;
                };
            case 0:
                firstMain = true;
                break;
            case 1:
                firstMain = true;
                secondMain = true;
                break;
            case 2:
                secondMain = true;
                break;
            case 3:
                secondMain = true;
                thirdMain = true;
                break;
            case 4:
                thirdMain = true;
                break;
            case 5:
                firstMain = true;
                thirdMain = true;
                break;

        }
        return switch (shade) {
            case 0 -> new Byte[]{
                    (firstMain ? light : medium),
                    (secondMain ? light : medium),
                    (thirdMain ? light : medium)
            };
            case 1 -> new Byte[]{
                    (firstMain ? light : dark),
                    (secondMain ? light : dark),
                    (thirdMain ? light : dark)
            };

            case 2 -> new Byte[]{
                    (firstMain ? medium : dark),
                    (secondMain ? medium : dark),
                    (thirdMain ? medium : dark)
            };

            default -> null;
        };
    }
}

record ColorChange(int hue, int shade){}

// Global value can be passed into function that changes it and come out changed
class GlobalValue <T>{
    public T value;

    public GlobalValue(T initial){ value = initial;}
}

// Custom Exceptions
// Command's format is found to be invalid while parsing
class CommandFormatError extends Exception{
    public CommandFormatError(RawFunction function, String command, String reason){
        super("\nError parsing \"" + command + "\" in " + function + "\n\t reason: " + reason);
    }
}

// can be thrown by functions to jump to try-catch block in main, ending program early
class TerminateProgram extends RuntimeException{}

// can be thrown in functions to jump out of if statements early
class JumpOut extends Exception{}
