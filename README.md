# Movie List

### What is this?

Fun personal project, that's all. You can read the code if you like.

### What does this do?

- Log into CSFD.cz using given credentials
- Navigates to 'Chci videt' (Watchlist)
- Expands the Watchlist movies (in parallel) and reads private notes
  - These are used to store which service I wanna watch the movie on, such as Netflix, HBO, Apple TV+ etc.
- Associates the movies by corresponding services
- Uses my Trello board, where it adds a bunch of lists (one list per movie service) and then adds movies to the appropriate lists

### Running

`./gradlew run --args='--csfd-login=user --csfd-pw=pw --trello-key=key --trello-token=token'`