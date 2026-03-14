import sys

def check_braces(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    stack = []
    for i, char in enumerate(content):
        if char == '{':
            # Find line number
            line_num = content.count('\n', 0, i) + 1
            stack.append(line_num)
        elif char == '}':
            line_num = content.count('\n', 0, i) + 1
            if not stack:
                print(f"Stray '}}' at line {line_num}")
                return
            stack.pop()
    
    if stack:
        for line in stack:
            print(f"Unclosed '{{' starting at line {line}")
    else:
        print("Braces are balanced.")

if __name__ == "__main__":
    check_braces(sys.argv[1])
