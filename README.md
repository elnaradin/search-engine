# Search engine
## Description

This program helps to find pages inside the given sites.
### 1. Dashboard
On the first tab there is a dashboard that shows the state of **the websites that are to be indexed**.
#####
![1 tab](https://user-images.githubusercontent.com/116953743/213195246-bf3a674b-e689-44ee-a76d-af639731bdbf.png)
#####
#### There are 3 states:
1. INDEXING – the site is in the process of being indexed
2. INDEXED – the site has been indexed successfully
3. FAILED – an error has occurred or the user has stopped the process and the site is not indexed completely.
### 2. Management
The button *"Start indexing"* clears all the previous data and starts the indexation process all over again.
Also particular pages of the given websites can be indexed or updated separately below the button.
#####
![2 tab](https://user-images.githubusercontent.com/116953743/213194350-613be967-5210-4606-b0c5-8055f0eea534.png)
#####
### 3. Search
Here is the search bar. You can choose the site you want to get pages from in the drop-down list above.
#####
![3 tab](https://user-images.githubusercontent.com/116953743/213194688-a84d9bbe-9f6a-42cb-bb1e-99405c080ce9.png)
####
## Technologies
#### The technolologies used in the creation of this progect are the following:
1. spring-boot-starter-parent
2. spring-boot-starter-web
3. spring-boot-starter-thymeleaf
4. spring-boot-starter-data-jpa
5. mysql-connector-java
6. jsoup
7. lombok
8. morph  <sub>(org.apache.lucene.morphology and the following too)</sub>
9. morphology
10. dictionary-reader
11. english
11. russian
## Installation
1. Create a database in MySQL Workbench. 
#####
![database](https://user-images.githubusercontent.com/116953743/213188389-b4577dfa-8bf0-43bf-8a14-1f8c5948526b.png)
#####
2. Open the file **application.yaml** in the project and change the password to the one that you have.
#####
![password](https://user-images.githubusercontent.com/116953743/213189429-ab4d0091-9beb-4cd1-af07-a13526abc8ca.png)
#####
3. Run it and go to http://localhost:8080/ in your browser.
