package searchengine.services.indexation;


import searchengine.dto.statistics.Response;

public interface IndexationService {
    String[] errors = {"Индексация уже запущена",
            "Индексация не запущена",
            "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле"};
    String IS_STOPPED_BY_USER_MESSAGE = "Индексация остановлена пользователем";
    Response startIndexingAndGetResponse();
    Response stopIndexingAndGetResponse();
    Response indexPageAndGetIndexPageResponse(String url);

    boolean isIndexing();




}

