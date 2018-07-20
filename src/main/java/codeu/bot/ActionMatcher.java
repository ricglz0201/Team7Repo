package codeu.bot;

import codeu.bot.BotActions;
import codeu.model.store.basic.ConversationStore;
import codeu.model.store.basic.UserStore;
import codeu.model.store.persistence.PersistentDataStoreException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import com.google.appengine.repackaged.com.google.common.flogger.parser.ParseException;

import org.apache.commons.text.similarity.LevenshteinDistance;

import com.google.cloud.language.v1.AnalyzeSyntaxRequest;
import com.google.cloud.language.v1.AnalyzeSyntaxResponse;
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.Document.Type;
import com.google.cloud.language.v1.EncodingType;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.language.v1.Token;
import com.google.cloud.language.v1.PartOfSpeech.Tag;
import com.google.cloud.language.v1.DependencyEdge.Label;

import com.joestelmach.natty.*;

public class ActionMatcher {
    private static String input;
    private static LevenshteinDistance distance;

    private BotActions botActions;

    private LanguageServiceClient language;

    private List<Token> tokensList;
    private List<Tag> tokensPOSTagsList;
    private List<String> tokensContentsList;
    private List<String> tokensLemmasList;

    private HashSet<String> conversationTitles;
    private HashSet<String> userNames;
    // Hard coded for now? Not sure if a store is going to be implemented for this
    public HashSet<String> settingNames = new HashSet<>(Arrays.asList(new String[] {"background color"}));


    private static final String[] SET_KEYWORDS = new String[] {"set","update","change"};
    private static final String[] CREATE_KEYWORDS = new String[] {"create","make","by"};
    private static final String[] GET_KEYWORDS = new String[] {"get","find","display","show","give"}; 
    private static final String[] SUMMARIZE_KEYWORDS = new String[] {"summarize","summarise","overview","TLDR"};
    private static final String[] UNREAD_KEYWORDS = new String[] {"unread","response","reply","respond"};
    private static final String[] NAVIGATE_KEYWORDS = new String[] {"navigate","take"};
    private static final String[] HELP_KEYWORDS = new String[] {"help","tutorial","guide"};
    private static final String[] ABOUT_KEYWORDS = new String[] {"contain","about","mention","like"};

    private static final String[] BACKGROUND_COLORS = new String[] {"white","black","grey","gray","red","orange","yellow","green","blue","indigo","violet","purple"};
    private static final HashSet<String> STATS = new HashSet<>(Arrays.asList(new String[] {"conversation count","user count","message count","most active user", "most active conversation"}));
    private static final String[] PAGES = new String[] {"activity","profile","conversations","setting"};

    private static final Pattern doubleQuotesPattern = Pattern.compile("\"([^\"]*)\"");
    private Matcher doubleQuotesMatcher;

    private boolean actionMatched;
    private HttpServletResponse httpServletResponse;

    private static ActionMatcher actionMatcherInstance;

    public Parser parser;
    public List<DateGroup> groups;

    private ActionMatcher() {
        distance = new LevenshteinDistance();
        parser = new Parser();
    }

    public static ActionMatcher getInstance() {
        if (actionMatcherInstance == null) {
            actionMatcherInstance = new ActionMatcher();
        }
        return actionMatcherInstance;
    }

    public boolean foundAndIsRoot(int index) {
        return (index != -1 && tokensList.get(index).getDependencyEdge().getLabel().equals(Label.ROOT));
    }

    public String findFuzzyMatch(HashSet<String> set, int distanceThreshold) {
        System.out.println(distance);
        String ret = "";
        ArrayList<Integer> spaceIndices = new ArrayList<Integer>();

        int findIndex = input.indexOf(" ", 0);
        while (findIndex >= 0) {
            spaceIndices.add(findIndex);
            findIndex = input.indexOf(" ", findIndex+1);
        }

        outerloop:
        for (String element : set) {
            for (Integer spaceIndex : spaceIndices) {
                if (spaceIndex.intValue() + element.length() >= input.length()) {
                    break;
                } else {
                    if (distance.apply(input.substring(spaceIndex, spaceIndex + element.length()), element).intValue() < distanceThreshold) {
                        ret = element;
                        break outerloop;
                    }
                }
            }
        }

        return ret;
    }

    public int getKeywordIndex(String[] keywords, List<String> lemmas) {
        int index = -1;
        int keywordsIndex = 0;

        while (index < 0 && keywordsIndex < keywords.length) {
            index = lemmas.indexOf(keywords[keywordsIndex]);
            keywordsIndex++;
        }

        return index;
    }

    private void matchSet() throws IOException, PersistentDataStoreException {
        int setOrUpdateOrChangeIndex = getKeywordIndex(SET_KEYWORDS, tokensLemmasList);
        if (foundAndIsRoot(setOrUpdateOrChangeIndex)) {

            int descIndex = tokensLemmasList.indexOf("description");
            String description = "";
            if (descIndex != -1) {
                // Find set description parameter
                Matcher matcher = doubleQuotesPattern.matcher(input);
                if (matcher.find()) {
                    description = matcher.group(1);
                    System.out.printf("Setting description to %s.",description);
                    BotActions.Action.SET_DESCRIPTION.doAction(description);
                    actionMatched = true;
                    return;
                } else {
                    BotActions.Action.MISSING_PARAMETER.doAction();
                    actionMatched = true;
                    return;
                }
            }

            // String setting = findFuzzyMatch(settingNames, 3);
            // if (!setting.isEmpty()) {
            //     if (setting.contains("color") || setting.contains("colour")) {
            //         // Going to be setting a color

            //         String color = "";
            //         int colorIndex = getKeywordIndex(BACKGROUND_COLORS, tokensLemmasList);
            //         if (colorIndex != -1) {
            //             color = tokensLemmasList.get(colorIndex);
            //             System.out.printf("Setting background color to %s.",color);
            //             BotActions.Action.SET_SETTING.doAction(setting, color);
            //             actionMatched = true;
            //             return;
            //         } else {
            //             BotActions.Action.MISSING_PARAMETER.doAction();
            //             actionMatched = true;
            //             return;
            //         }
            //     }
            // } else {

            // }
            // return;
        }
        return;
    }

    private void matchCreateConversation() throws IOException, PersistentDataStoreException {
        int createOrMakeIndex = getKeywordIndex(CREATE_KEYWORDS, tokensLemmasList);
        if (foundAndIsRoot(createOrMakeIndex)) {
            // Going to be creating something
            // Right now we can only create conversations so don't need to
            // look for conversation keyword

            String conversationTitle = "";
            Matcher matcher = doubleQuotesPattern.matcher(input);
            if (matcher.find()) {
                conversationTitle = matcher.group(1);
                System.out.printf("Creating conversation %s.",conversationTitle);
                BotActions.Action.CREATE_CONVERSATION.doAction(conversationTitle);
                actionMatched = true;
                return;
            } else {
                BotActions.Action.MISSING_PARAMETER.doAction();
                actionMatched = true;
                return;
            }
        }
        return;
    }

    private void matchSendMessage() throws IOException, PersistentDataStoreException {
        int sendIndex = tokensLemmasList.indexOf("send");
        if (foundAndIsRoot(sendIndex)) {
            // Going to be sending something
            // Same logic for create, know we're looking for a message and conversation

            String message = "";
            Pattern messagePattern = Pattern.compile("\"([^\"]*)\"");
            Matcher messageMatcher = messagePattern.matcher(input);
            if (messageMatcher.find()) {
                message = messageMatcher.group(1);
                String conversationTitle = findFuzzyMatch(conversationTitles, 5);
                System.out.printf("Sending message \"%s\" in conversation <%s>.",message,conversationTitle);
                BotActions.Action.SEND_MESSAGE.doAction(message,conversationTitle);
                actionMatched = true;
                return;
            } else {
                BotActions.Action.MISSING_PARAMETER.doAction();
                actionMatched = true;
                return;
            }
        }
        return;
    }

    public void matchGet() throws IOException, PersistentDataStoreException {
        int getOrFindOrDisplayOrShoworGiveIndex = getKeywordIndex(GET_KEYWORDS, tokensLemmasList);
        if (foundAndIsRoot(getOrFindOrDisplayOrShoworGiveIndex)) {
            String directObject = "";

            List<Token> directObjects = tokensList.stream().
                filter(token -> token.getDependencyEdge().
                getLabel().equals(Label.DOBJ)).
                collect(Collectors.toList());

            if (directObjects.size() == 1) {
                directObject = directObjects.get(0).getLemma();
            } else {
                return;
            }

            switch (directObject) {
                case "message": {
                    matchGetMessages();
                    break;
                }
                case "conversation": {
                    matchGetConversations();
                    break;
                }
            }

            if (actionMatched) {
                return;
            }   
                
            String setting = findFuzzyMatch(settingNames, 3);
            if (!setting.isEmpty()) {
                System.out.printf("Getting %s.",setting);
                BotActions.Action.GET_SETTING.doAction(setting);
                actionMatched = true;
                return;
            }

            if (input.contains("settings")) {
                System.out.println("Getting all settings.");
                BotActions.Action.GET_SETTINGS.doAction();
                actionMatched = true;
                return;
            }

            // STATISTICS
            String statistic = findFuzzyMatch(STATS, 5);
            if (!statistic.isEmpty()) {
                System.out.printf("Getting %s.",statistic);
                BotActions.Action.GET_STAT.doAction(statistic);
                actionMatched = true;
                return;
            }

            if (input.contains("statistics") || input.contains("stats")) {
                System.out.println("Getting all statistics.");
                BotActions.Action.GET_STATS.doAction();
                actionMatched = true;
                return;
            }
            
        }
        return;
    }

    public void matchGetMessages() throws IOException {
        // GET_MY_MESSAGES
        if (tokensLemmasList.contains("my") || tokensLemmasList.contains("i")) {
            System.out.printf("Showing your messages.");
            BotActions.Action.GET_MY_MESSAGES.doAction();
            actionMatched = true;
            return;
        }

        // GET_MESSAGES_BY_CREATION_TIME
        if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
            System.out.println(groups.get(0).getDates());
            BotActions.Action.GET_MESSAGES_BY_CREATION_TIME.doAction(groups.get(0).getDates().get(0).toInstant());
        }

        // GET_MESSAGES_LIKE_KEYWORD
        int containOrAboutOrMentionIndex = getKeywordIndex(ABOUT_KEYWORDS, tokensLemmasList);
        if (containOrAboutOrMentionIndex != -1) {
            String keyword = "";
            Matcher keywordMatcher = doubleQuotesPattern.matcher(input);
            if (keywordMatcher.find()) {
                keyword = keywordMatcher.group(1);
                System.out.printf("Getting messages like keyword \"%s\".",keyword);
                BotActions.Action.GET_MESSAGES_LIKE_KEYWORD.doAction(keyword);
                actionMatched = true;
                return;
            } else {
                BotActions.Action.MISSING_PARAMETER.doAction();
                actionMatched = true;
                return;
            }
        }

        // GET_MESSAGES_FROM_CONVERSATION
        int fromOrInIndex = getKeywordIndex(new String[] {"in","from"}, tokensLemmasList);
        if (fromOrInIndex != -1) {
            String conversationTitle = findFuzzyMatch(conversationTitles, 3);
            if (!conversationTitle.isEmpty()) {
                System.out.printf("Getting messsages from %s.",conversationTitle);
                BotActions.Action.GET_MESSAGES_FROM_CONVERSATION.doAction(conversationTitle);
                actionMatched = true;
                return;
            }
        }
        return;
    }

    public void matchGetConversations() throws IOException, PersistentDataStoreException {
        // GET_CONVERSATIONS_BY_CREATION_TIME
        if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
            System.out.println(groups.get(0).getDates());
            BotActions.Action.GET_CONVERSATIONS_BY_CREATION_TIME.doAction(groups.get(0).getDates().get(0).toInstant());
        }

        // GET_CONVERSATIONS_BY_AUTHOR
        int madeOrCreateOrOwnOrByIndex = getKeywordIndex(CREATE_KEYWORDS, tokensLemmasList);
        if (madeOrCreateOrOwnOrByIndex != -1) {
            String author = findFuzzyMatch(userNames, 3);
            if (!author.isEmpty()) {
                System.out.printf("Getting conversations made by %s.",author);
                BotActions.Action.GET_CONVERSATIONS_BY_AUTHOR.doAction(author);
                actionMatched = true;
                return;
            }
        }

        // GET_CONVERSATIONS_WITH_UNREAD_MESSAGES
        int unreadOrRespondIndex = getKeywordIndex(UNREAD_KEYWORDS, tokensLemmasList);
        if (unreadOrRespondIndex != -1) {
            System.out.printf("Getting conversations with unread messages.");
            BotActions.Action.GET_CONVERSATIONS_WITH_UNREAD_MESSAGES.doAction();
            actionMatched = true;
            return;
        }

        // GET_CONVERSATIONS_ABOUT_KEYWORD (encompass the like and content methods)
        int containOrAboutOrMentionIndex = getKeywordIndex(ABOUT_KEYWORDS, tokensLemmasList);
        if (containOrAboutOrMentionIndex != -1) {
            String keyword = "";
            Matcher keywordMatcher = doubleQuotesPattern.matcher(input);
            if (keywordMatcher.find()) {
                keyword = keywordMatcher.group(1);
                System.out.printf("Getting conversations about keyword \"%s\".",keyword);
                BotActions.Action.GET_CONVERSATIONS_ABOUT_KEYWORD.doAction(keyword);
                actionMatched = true;
                return;
            } else {
                BotActions.Action.MISSING_PARAMETER.doAction();
                actionMatched = true;
                return;
            }
        }

        // GET_ALL_CONVERSATIONS
        int allIndex = getKeywordIndex(new String[] {"all"}, tokensLemmasList);
        if (allIndex != -1) {
            System.out.printf("Getting all conversations.");
            BotActions.Action.GET_ALL_CONVERSATIONS.doAction();
            actionMatched = true;
            return;
        }
    }

    public void matchSummarize() throws IOException {
        int summarizeOrSummariseOrOverviewOrTLDRIndex = getKeywordIndex(
            SUMMARIZE_KEYWORDS,
            tokensLemmasList);
        if (foundAndIsRoot(summarizeOrSummariseOrOverviewOrTLDRIndex)) {
            String conversationTitle = findFuzzyMatch(conversationTitles,3);
            if (!conversationTitle.isEmpty()) {
                System.out.printf("Getting a summary of %s.",conversationTitle);
                BotActions.Action.GET_CONVERSATION_SUMMARY.doAction(conversationTitle);
                actionMatched = true;
                return;
            } else {
                BotActions.Action.MISSING_PARAMETER.doAction();
                actionMatched = true;
                return;
            }
        }
        return;
    }

    public void matchNavigate() throws IOException {
        int navigateOrTakeIndex = getKeywordIndex(NAVIGATE_KEYWORDS, tokensLemmasList);
        if (foundAndIsRoot(navigateOrTakeIndex)) {
            
            String conversation = findFuzzyMatch(conversationTitles, 3);
            if (!conversation.isEmpty()) {
                System.out.printf("Taking you to %s.",conversation);
                BotActions.Action.NAVIGATE_TO_CONVERSATION.doAction(conversation,httpServletResponse);
                actionMatched = true;
                return;
            }

            int pageIndex = getKeywordIndex(PAGES, tokensLemmasList);
            if (pageIndex != -1) {
                String page = tokensLemmasList.get(pageIndex);
                System.out.printf("Taking you to %s page.",page);
                BotActions.Action.NAVIGATE.doAction(page,httpServletResponse);
                actionMatched = true;
                return;
            }
        }
        return;
    }

    public void matchHelp() throws IOException {
        if (getKeywordIndex(HELP_KEYWORDS,tokensLemmasList) != -1) {
            BotActions.Action.GET_HELP.doAction();
            actionMatched = true;
            return;
        }
        return;
    }


    public void matchAction(String text, String username, HttpServletResponse httpServletResponse) throws Exception, IOException, PersistentDataStoreException {
        System.out.println("Attempting to match action.");
        actionMatched = false;
        this.input = text;
        this.httpServletResponse = httpServletResponse;

        conversationTitles = ConversationStore.getInstance().getAllConversationTitles();
        userNames = UserStore.getInstance().getAllUserNames();

        botActions = new BotActions(username);

        // [START analyze_syntax_text]
        // Instantiate the Language client com.google.cloud.language.v1.LanguageServiceClient
        try (LanguageServiceClient language = LanguageServiceClient.create()) {
            Document doc = Document.newBuilder()
                .setContent(text)
                .setType(Type.PLAIN_TEXT)
                .build();
            AnalyzeSyntaxRequest request = AnalyzeSyntaxRequest.newBuilder()
                .setDocument(doc)
                .setEncodingType(EncodingType.UTF16)
                .build();
            // analyze the syntax in the given text
            AnalyzeSyntaxResponse response = language.analyzeSyntax(request);
            // print the response

            tokensList = response.getTokensList();

            for (Token token : tokensList) {
                System.out.printf("\tText: %s\n", token.getText().getContent());
                System.out.printf("\tBeginOffset: %d\n", token.getText().getBeginOffset());
                System.out.printf("Lemma: %s\n", token.getLemma());
                System.out.printf("PartOfSpeechTag: %s\n", token.getPartOfSpeech().getTag());
                System.out.printf("\tAspect: %s\n", token.getPartOfSpeech().getAspect());
                System.out.printf("\tCase: %s\n", token.getPartOfSpeech().getCase());
                System.out.printf("\tForm: %s\n", token.getPartOfSpeech().getForm());
                System.out.printf("\tGender: %s\n", token.getPartOfSpeech().getGender());
                System.out.printf("\tMood: %s\n", token.getPartOfSpeech().getMood());
                System.out.printf("\tNumber: %s\n", token.getPartOfSpeech().getNumber());
                System.out.printf("\tPerson: %s\n", token.getPartOfSpeech().getPerson());
                System.out.printf("\tProper: %s\n", token.getPartOfSpeech().getProper());
                System.out.printf("\tReciprocity: %s\n", token.getPartOfSpeech().getReciprocity());
                System.out.printf("\tTense: %s\n", token.getPartOfSpeech().getTense());
                System.out.printf("\tVoice: %s\n", token.getPartOfSpeech().getVoice());
                System.out.println("DependencyEdge");
                System.out.printf("\tHeadTokenIndex: %d\n", token.getDependencyEdge().getHeadTokenIndex());
                System.out.printf("\tLabel: %s\n\n", token.getDependencyEdge().getLabel());
            }

            tokensPOSTagsList = tokensList.stream().
                map(token -> token.getPartOfSpeech().getTag()).
                collect(Collectors.toList());

            tokensContentsList = tokensList.stream().
                map(token -> token.getText().getContent()).
                collect(Collectors.toList());

            tokensLemmasList = tokensList.stream().
                map(token -> token.getLemma()).
                collect(Collectors.toList());

            groups = parser.parse(input);
            System.out.println(groups);

            matchSet();
            if (!actionMatched) {
                matchSendMessage();
                if (!actionMatched) {
                    matchCreateConversation();
                    if (!actionMatched) {
                        matchGet();
                        if (!actionMatched) {
                            matchSummarize();
                            if (!actionMatched) {
                                matchNavigate();
                                if (!actionMatched) {
                                    matchHelp();
                                }
                            }
                        }
                    }
                }
            }
    
            if (!actionMatched) {
                BotActions.Action.NOT_FOUND.doAction();
            } else {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            BotActions.Action.NOT_FOUND.doAction();
        }
    }
}
