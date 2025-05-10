package machinum;

import lombok.extern.slf4j.Slf4j;
import machinum.service.plugin.StatisticPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class LLMApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(LLMApplication.class, args);
        context.getBean(StatisticPlugin.class).init();
    }
//
////	@Bean
//	ApplicationRunner applicationRunner(JdbcTemplate template, DocsLoaderService docsLoaderService, Chatbot chatbot, ObjectMapper objectMapper, TemplateAiFacade2 service) {
//		return args -> {
//			try {
//				System.out.println("LLMApplication.applicationRunner");
////				try3(objectMapper, service);
////				try2(objectMapper, service);
//
////				try1(template, docsLoaderService, chatbot);
//			} catch (Exception e) {
//				log.error("ERROR: ", e);
//			}
//		};
//	}
//
//	private static void try3(ObjectMapper objectMapper, TemplateAiFacade2 service) throws IOException {
//		Path chapterPath = Path.of("src/main/resources/docs/chapter_02-rewrite.md");
////		Path chapterPath = Path.of("src/main/resources/docs/chapter_02.md");
////		Path chapterPath = Path.of("src/main/resources/docs/colors_03-rewrite.md");
////		Path chapterPath = Path.of("src/main/resources/docs/colors_03.md");
////		Path chapterPath = Path.of("src/main/resources/docs/colors_02.md");
////		Path chapterPath = Path.of("src/main/resources/docs/colors_01.md");
////		var responseRewrite = DurationUtil.measure("rewrite", () ->
////				service.rewrite(Files.readString(chapterPath)));
//
////		var rewrite = DurationUtil.measure("rewrite", () ->
////				service.rewrite(Files.readString(chapterPath)));
//
////		var summary = DurationUtil.measure("summary", () ->
////				service.summary(rewrite.result()));
////		var responseKeywords = DurationUtil.measure("keywords", () ->
////				service.keywords(Files.readString(chapterPath)));
////		var selfConsistency = DurationUtil.measure("selfConsistency", () ->
////				service.selfConsistency(Files.readString(chapterPath)));
////		var quotes = DurationUtil.measure("quotes", () ->
////				service.quotes(Files.readString(chapterPath)));
////		var characters = DurationUtil.measure("characters", () ->
////				service.characters(Files.readString(chapterPath)));
////		var themes = DurationUtil.measure("themes", () ->
////				service.themes(Files.readString(chapterPath)));
////		var perspective = DurationUtil.measure("perspective", () ->
////				service.perspective(Files.readString(chapterPath)));
////		var tone = DurationUtil.measure("tone", () ->
////				service.tone(Files.readString(chapterPath)));
////		var foreshadowing = DurationUtil.measure("foreshadowing", () ->
////				service.foreshadowing(Files.readString(chapterPath)));
////		var names = DurationUtil.measure("names", () ->
////				service.names(Files.readString(chapterPath)));
////		var scenes = DurationUtil.measure("scenes", () ->
////				service.scenes(Files.readString(chapterPath)));
//		var proofread = DurationUtil.measure("proofread", () ->
//				service.proofread(Files.readString(chapterPath)));
//
//		var value = objectMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsBytes(Map.of(
////						"rewrite", rewrite.result(),
////						"questions", selfConsistency.result().questions(),
////						"summary", summary.result(),
////						"keywords", responseKeywords.result(),
////						"answers", selfConsistency.result().answers(),
////						"quotes", quotes.result(),
////						"characters", characters.result()
////						"themes", themes.result(),
////						"perspective", perspective.result(),
////						"tone", tone.result(),
////						"foreshadowing", foreshadowing.result()
////						"names", names.result(),
////						"scenes", scenes.result()
//						"proofread", proofread.result()
//				));
//
//		Files.write(Path.of("output/chapter_02.json"), value,
//				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//
//		System.out.println("LLMApplication.applicationRunner: " + LocalDateTime.now());
//	}
//
//	private static void try2(ObjectMapper objectMapper, TemplateAiFacade2 service) throws IOException {
//		Path chapterPath = Path.of("src/main/resources/docs/chapter_02.md");
//		var response = service.processText(Map.of(
//					"text", Files.readString(chapterPath)
//				),
//				"Rewrite.ST",
////				"Summary.ST",
////				"Argument.ST",
////				"Concision.ST",
//				"Questions.ST"
////				"Answer.ST"
////				"Quotes.ST"
//		);
//
//		var value = objectMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsBytes(response);
//
//		Files.write(Path.of("output/chapter_02.json"), value,
//				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//
//		System.out.println("LLMApplication.applicationRunner: " + response);
//	}
//
//	private static void try1(JdbcTemplate template, DocsLoaderService docsLoaderService, Chatbot chatbot) {
//		template.update("delete from vector_store_384");
//
////				docsLoaderService.testMethod(new ClassPathResource("docs/devcenter-content-snapshot.2024-05-21.json"));
//		log.info("Prepare to create knowledge base...");
//		docsLoaderService.loadDocs2();
////				docsLoaderService.loadDocs();
//
//		log.info("Asking a question...");
//		var response = chatbot.chat("""
//				  For chapter 2, create metadata in json, return chapter summary and characters list. Print only json without explanation
//				""");
//		System.out.println(Map.of("response", response));
//	}

}
