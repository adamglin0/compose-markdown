# Full Markdown Demo

Adapted from the "Full-Markdown.md" example by allysonsilva.

## Headers

# h1 Heading 8-)
## h2 Heading
### h3 Heading
#### h4 Heading
##### h5 Heading
###### h6 Heading

Alt-H1
======

Alt-H2
------

---

## Emphasis

Emphasis, aka italics, with *asterisks* or _underscores_.

Strong emphasis, aka bold, with **asterisks** or __underscores__.

Combined emphasis with **asterisks and _underscores_**.

Strikethrough uses two tildes. ~~Scratch this.~~

**This is bold text**

__This is bold text__

*This is italic text*

_This is italic text_

~~Strikethrough~~

---

## Lists

1. First ordered list item
2. Another item
   - Unordered sub-list
3. Actual numbers do not matter, just that it is a number
   1. Ordered sub-list
4. And another item

- Unordered list can use asterisks
- Or minuses
- Or pluses

1. Make my changes
   1. Fix bug
   2. Improve formatting
      - Make the headings bigger
2. Push my commits to GitHub
3. Open a pull request
   - Describe my changes
   - Mention all the members of my team

---

## Task Lists

- [x] Finish my changes
- [ ] Push my commits to GitHub
- [ ] Open a pull request
- [x] Formatting and [links](https://www.google.com) supported
- [ ] This is an incomplete item

---

## Escaping

Let's rename \*our-new-project\* to \*our-old-project\*.

---

## Links

[I'm an inline-style link](https://www.google.com)

[I'm an inline-style link with title](https://www.google.com "Google's Homepage")

[I'm a reference-style link][arbitrary case-insensitive reference text]

[You can use numbers for reference-style link definitions][1]

Or leave it empty and use the [link text itself].

URLs and URLs in angle brackets will automatically get turned into links.
http://www.example.com or <http://www.example.com>

Some text to show that the reference links can follow later.

[arbitrary case-insensitive reference text]: https://www.mozilla.org
[1]: http://slashdot.org
[link text itself]: http://www.reddit.com

---

## Images

![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png "Logo Title Text 1")

![Minion](https://octodex.github.com/images/minion.png)

Reference-style image:

![alt text][logo]

[logo]: https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png "Logo Title Text 2"

---

## Code

Inline `code` has `back-ticks around` it.

```csharp
using System.IO.Compression;

namespace MyApplication
{
    class Program
    {
        public static List<int> JustDoIt(int count)
        {
            Console.WriteLine($"Hello {count}!");
            return new List<int>(new int[] { 1, 2, 3 });
        }
    }
}
```

```javascript
function initHighlight(block, cls) {
  try {
    if (cls.search(/\bno\-highlight\b/) !== -1) {
      return process(block, true, 0x0F);
    }
  } catch (error) {
    console.log(error);
  }

  for (let i = 0; i < classes.length; i += 1) {
    if (checkCondition(classes[i]) === undefined) {
      console.log('undefined');
    }
  }
}

export default initHighlight;
```

---

## Tables

| Tables        | Are           | Cool  |
| ------------- |:-------------:| -----:|
| col 3 is      | right-aligned | $1600 |
| col 2 is      | centered      |   $12 |
| zebra stripes | are neat      |    $1 |

Markdown | Less | Pretty
--- | --- | ---
*Still* | `renders` | **nicely**
1 | 2 | 3

| Command | Description |
| --- | --- |
| `git status` | List all *new or modified* files |
| `git diff` | Show file differences that **haven't been** staged |

---

## Blockquotes

> Blockquotes are very handy in email to emulate reply text.
> This line is part of the same quote.

Quote break.

> This is a very long line that will still be quoted properly when it wraps.
> Oh, you can *put* **Markdown** into a blockquote.

> Blockquotes can also be nested...
>> ...by using additional greater-than signs right next to each other...
> > > ...or with spaces between arrows.

---

## Horizontal Rules

Three or more...

---

Hyphens

***

Asterisks

___

Underscores
