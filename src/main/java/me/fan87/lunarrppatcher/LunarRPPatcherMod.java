package me.fan87.lunarrppatcher;

import me.fan87.javainjector.NativeInstrumentation;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;


import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

public class LunarRPPatcherMod implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger();

	private static final String LOG_PREFIX = "[lunar-rp-patcher] ";

	@Override
	public void onInitialize() {
		try {
			init();
		} catch (Throwable e) { // Meaning, this will not crash the client even if it's not a lunar client instance'
			LOGGER.error(LOG_PREFIX + "Failed to load Lunar ResourcePack Patcher", e);
		}

	}

	private void init() throws IOException {
		// // Pre-init NativeAgent, a library that allows you to redefine classes during runtime
		LOGGER.info(LOG_PREFIX + "Initializing NativeInstrumentation...");
		NativeInstrumentation.init();

		// Search for lunar.jar
		LOGGER.info(LOG_PREFIX + "Current Path: {}", System.getProperty("user.dir"));
		File lunarJar = findLunarJar();

		// Find matching class & Patch
		LOGGER.info(LOG_PREFIX + "Searching for matching class in {}", lunarJar.getAbsolutePath());
		try (JarInputStream inputStream = new JarInputStream(new FileInputStream(lunarJar))) { // Open lunar jar
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				try {
					// Look for every class file
					if (!entry.getName().endsWith(".class")) continue;
					if (entry.isDirectory()) continue;
					if (processClass(inputStream)) {
						return;
					}
				} finally {
					inputStream.closeEntry();
				}
			}
		}
		throw new IllegalStateException("Failed to patch - could not find matching classes & methods");
    }

    // This will process the class (check if it matches etc.)
    // Risky check, but works at the time of development
	private boolean processClass(InputStream stream) {
		ClassNode classNode = new ClassNode();
		ClassReader reader = new ClassReader(stream);
		reader.accept(classNode, ClassReader.EXPAND_FRAMES);
		if (!rule1Matches(classNode)) return false;
		LOGGER.info(LOG_PREFIX + "Rule Matches #1: {}", classNode.name);
		MethodNode method = findTargetMethod(classNode);
		if (method == null) {
			LOGGER.warn(LOG_PREFIX + "Rule #2 does not match, which is unexpected at the time of development");
			return false;
		}
		LOGGER.info(LOG_PREFIX + "Rule Matches #2: {}, method: {}{}", classNode.name, method.name, method.desc);
		LOGGER.info(LOG_PREFIX + "Adding patcher to {}...", classNode.name);
		registerTransformer(classNode.name);
		LOGGER.info(LOG_PREFIX + "Patcher has been successfully registered");

		return true;
	}

	private void registerTransformer(String targetClassName) {
		// The bug occured, because Lunar client was attempting to (I assume) search for a single texture pack recursively, even into the `assets/` directory
		// Which you may guessed - when you have a lot of unzipped resource packs, will take a very long time - especially on Windows (because the file API is slow for some reason)
		// This part of the code fixes the bug by not going into any `assets/` directory - which, yes, will prevent any resource pack with `assets/` as name from loading,
		// But nobody's gonna use it anyway, and you can always rename your resource pack directory name.'
		NativeInstrumentation instrumentation = new NativeInstrumentation();
		instrumentation.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if (!className.equals(targetClassName)) return null;
				// Reads the class in...
				ClassNode classNode = new ClassNode();
				ClassReader reader = new ClassReader(classfileBuffer);
				reader.accept(classNode, ClassReader.EXPAND_FRAMES);
				MethodNode targetMethod = findTargetMethod(classNode);

				// Setup some variables
				InsnList out = new InsnList();
				LabelNode regular = new LabelNode();

				// TL;DR
				// if (file.getName().equals("assets")) return null;


				// Check if file name is "assets"
				out.add(new VarInsnNode(Opcodes.ALOAD, 0));
				out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false));
				out.add(new LdcInsnNode("assets"));
				out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false));

				out.add(new JumpInsnNode(Opcodes.IFEQ, regular)); // Jump back to regular if the string is not equal to "assets"

				out.add(new InsnNode(Opcodes.ACONST_NULL)); // If it's not jumped, return null'
				out.add(new InsnNode(Opcodes.ARETURN));
				out.add(regular); // This is where the previous code will jump to (regular code)

				// Insert what it's supposed to be doing here'
				for (AbstractInsnNode instruction : targetMethod.instructions) {
					out.add(instruction);
				}

				// Write the method
				targetMethod.instructions = out;
				ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
				classNode.accept(writer);
				LOGGER.info(LOG_PREFIX + "Successfully patched the target class!");
				return writer.toByteArray();
			}
		}, true);
        try {
            instrumentation.retransformClasses(Class.forName(targetClassName.replace("/", ".")));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Basically, there will be 2 rules checking if the class is actually it
    // First, does it have a field with type FilenameFilter? (Rules out 99.9% of the classes)
    // Second, does it have a method with descriptor (Ljava/io/File;Ljava/lang/String;)Ljava/io/File;? (Rules out practically everything except our target class)
	private boolean rule1Matches(ClassNode classNode) {
		for (FieldNode field : classNode.fields) {
			if (field.desc.equals("Ljava/io/FilenameFilter;")) return true;
		}
		return false;
	}

	private MethodNode findTargetMethod(ClassNode classNode) {
		for (MethodNode method : classNode.methods) {
			if (!method.desc.equals("(Ljava/io/File;Ljava/lang/String;)Ljava/io/File;")) continue;
			return method;
		}
		return null;
	}


	// This just looks for the lunar client jar file
	private File findLunarJar() {
		if (!System.getProperty("user.dir").contains(".lunarclient")) {
			throw new IllegalStateException("Not a lunar client instance");
		}
		return new File("lunar.jar");
	}

}
