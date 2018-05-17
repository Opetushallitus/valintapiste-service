
### RUN EXAMPLE

`mvn compile exec:java -Dexec.args="mongoURI=mongodb://test:test@localhost jdbcURI=jdbc:postgresql://localhost:55432/test?user=test&password=test"`

### IF YOU NEED TO PIPE YOUR MONGO CONNECTION THROUGH ANOTHER SERVER

`ssh -N -L 27017:finalmongoserver:27017 yourusername@someserverthatcanaccessfinalmongoserver`

### IF YOU NEED TO PIPE YOUR POSTGRES CONNECTION THROUGH ANOTHER SERVER

`ssh -N -L 5432:finalpostgresserver:5432 yourusername@someserverthatcanaccessfinalpostgresserver`
