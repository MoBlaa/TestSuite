1. Die Klassen "TestSuite", "ExpectionInputStream", "ExpectionOutputStream" in das Projekt kopieren.

2. Beim Start der TestSuite-Klasse wird nach dem Ordner der Testfälle gefragt,
   berücksichtige dabei dass der Projekt Ordner als Start-Verzeichnis gilt.

3. Danach wird nach der zu testenden Klasse gefragt, gebe hier einfach den Namen der Klasse im Package
   "edu.kit.informatik" an, die als Main-Klasse gehandelt wird.

4. Die test-Dateien müssen so benannt sein: "*.test" und forlgender formatierung folgen:
        - Ein Testfall wird dargestellt als:      <expected> : "<actual>"
        - Wobei 'expected' eine Zeichenkette über mehrere Zeilen sein kann, die dem
          regulären Ausdruck [a-zA-Z0-9\\s]+ entspricht. 'expected' stellt dabei die erwartete Ausgabe dar.
          'expected' muss entweder "true", "false", einer Zahl oder einer Zeichenkette
          gekennzeichnet durch " entsprechen.
          'expected' kann nur als Zeichenkette mehrzeilig sein, solange der Zeilenumsprung
          in den " ist.

        - und 'actual' eine Zeichenkette über eine Zeile sein kann, die dem regulären Ausdruck
          [a-zA-Z0-9\\s-;]+ entspricht. 'actual' stellt dabei die Eingabe eines Befehls dar.
        - Die Kommandozeilenargumente werden dargestellt als:
                                                  <"cmd1";"cmd2";...>
          Wobei cmd1 ein Kommandozeilenargument darstellt.
          Die Kommandozeilenargumente müssen in der ersten Zeile der .test-Datei stehen.

5. Ein Beispiel für den Test-Fall auf dem Aufgabenblatt:

<"src\edu\kit\informatik\tests\test.graph">
6 : "search bB;d;route"
"bB Aa C d" : "route bB;d;route"
"bB Aa C d
bB Aa d
bB C Aa d
bB C d" : "route bB;d;all"
"Aa
C" : "nodes bB"